package dev.cue.draft

import dev.cue.model.CapturedContext
import dev.cue.model.ConversationStage
import dev.cue.model.Draft
import dev.cue.model.GateReport
import dev.cue.model.ModelTier
import dev.cue.model.SentMessage
import dev.cue.model.Strategy
import dev.cue.model.VoiceProfile
import dev.cue.voice.CompiledDraft
import dev.cue.voice.StyleVerifier
import dev.cue.voice.VoiceCompiler

/** A variant that produced nothing shippable, and why (§7.2, §13). */
data class SuppressedVariant(
    val strategy: Strategy,
    val reason: String,
    val gates: GateReport,
)

data class DraftSet(
    val conversationId: String,
    val stage: ConversationStage,
    /** In display order. The template draft is included when it leads (§6.5). */
    val drafts: List<Draft>,
    val suppressed: List<SuppressedVariant> = emptyList(),
    /** §6.3: surfaced as a banner, not a subtle variant label. */
    val readyToAsk: Boolean = false,
    /** §4.2: below 50 messages, say so and keep saying it. */
    val calibrating: Boolean = false,
    val totalInferenceMs: Long = 0L,
)

/**
 * §6–§7. Capture in, three drafts out.
 *
 * The sequence is the spec's, and the order of the gates is the part worth
 * reading twice: the voice compiler runs *before* every check, because §7.1's
 * style verification is checking the compiler's work, and §7.2's grounding gate
 * has to inspect the text that will actually be sent rather than the model's
 * draft of it.
 *
 * Failure handling is asymmetric on purpose:
 *
 * | Gate | Fails twice |
 * |---|---|
 * | §7.1 style | ship the best candidate, badge it off-voice |
 * | §7.2 grounding | ship **nothing** for that variant |
 * | §7.3 escalation | ship nothing for that variant |
 *
 * §7.2 states the reason plainly: a missing option is strictly better than a
 * hallucinated one. An off-voice draft is one you edit; a draft that invents a
 * dog is a message you cannot unsend.
 */
class DraftPipeline(
    private val voice: VoiceProfile,
    corpus: List<SentMessage>,
    private val engine: InferenceEngine?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
) {

    private val compiler = VoiceCompiler(voice)
    private val verifier = StyleVerifier(voice)
    private val grounding = GroundingGate(voice)
    private val index = Bm25Index(corpus)
    private val openerPatterns = TemplatePath.extractPatterns(corpus)
        .ifEmpty { TemplatePath.fallbackPatterns() }

    suspend fun draft(context: CapturedContext, lastTheirMessageAt: Long? = null): DraftSet {
        val stage = StageClassifier.classify(context, lastTheirMessageAt)
        val strategies = Strategy.forStage(stage)

        val shipped = mutableListOf<Draft>()
        val suppressed = mutableListOf<SuppressedVariant>()
        var inferenceMs = 0L

        if (engine != null) {
            strategies.forEach { strategy ->
                when (val outcome = attemptVariant(context, stage, strategy, emptyList())) {
                    is VariantOutcome.Shipped -> {
                        shipped += outcome.draft
                        inferenceMs += outcome.draft.inferenceMs
                    }

                    is VariantOutcome.Suppressed -> suppressed += outcome.variant
                }
            }
            inferenceMs += enforceDistinctness(context, stage, shipped)
        }

        val template = templateDraft(context, stage)
        val ordered = when {
            template == null -> shipped
            // §6.5: on the weakest tier the template path leads rather than
            // trails, because that is where generation is least trustworthy.
            engine == null || engine.tier.prefersTemplatePath -> listOf(template) + shipped
            else -> shipped + template
        }

        return DraftSet(
            conversationId = context.conversationId,
            stage = stage,
            drafts = ordered,
            suppressed = suppressed,
            readyToAsk = stage == ConversationStage.READY_TO_ASK,
            calibrating = !voice.isCalibrated,
            totalInferenceMs = inferenceMs,
        )
    }

    private sealed interface VariantOutcome {
        data class Shipped(val draft: Draft) : VariantOutcome
        data class Suppressed(val variant: SuppressedVariant) : VariantOutcome
    }

    /**
     * One variant, through as many attempts as §7 allows.
     *
     * [seedOffset] shifts the seed so a distinctness retry explores different
     * output rather than re-rolling the same one at a different temperature.
     */
    private suspend fun attemptVariant(
        context: CapturedContext,
        stage: ConversationStage,
        strategy: Strategy,
        initialConstraints: List<String>,
        seedOffset: Int = 0,
    ): VariantOutcome {
        val model = engine ?: return VariantOutcome.Suppressed(
            SuppressedVariant(strategy, "No model loaded", GateReport(attempts = 0)),
        )

        val examples = retrieveExamples(context, stage)
        val constraints = initialConstraints.toMutableList()
        var groundingFailures = 0
        var bestStyleCandidate: Draft? = null

        for (attempt in 0 until MAX_ATTEMPTS) {
            val prompt = promptBuilder.build(context, strategy, examples, voice, constraints)
            val startedAt = clock()
            val raw = try {
                model.generate(
                    GenerationRequest(
                        prompt = prompt.text,
                        seed = seedFor(strategy, attempt + seedOffset),
                        temperature = BASE_TEMPERATURE + TEMPERATURE_STEP * attempt,
                        maxTokens = maxTokensFor(),
                    ),
                )
            } catch (e: InferenceUnavailableException) {
                return VariantOutcome.Suppressed(
                    SuppressedVariant(strategy, e.message ?: "Model unavailable", GateReport(attempts = attempt + 1)),
                )
            }
            val elapsed = clock() - startedAt

            val compiled = compiler.compile(raw)
            if (compiled.needsRegeneration) {
                constraints += "Write a complete sentence. Do not open with filler."
                continue
            }

            val ungrounded = grounding.check(compiled.text, context)
            val escalation = EscalationGate.check(compiled.text, stage, context)
            val style = verifier.verify(compiled.text)
            val report = GateReport(
                styleDeviations = style,
                ungroundedTerms = ungrounded,
                escalationViolation = escalation,
                attempts = attempt + 1,
            )
            val draft = toDraft(context, strategy, raw, compiled, report, elapsed)

            when {
                // §7.2: regenerate with the offending term explicitly forbidden;
                // on second failure ship no draft for that variant.
                ungrounded.isNotEmpty() -> {
                    groundingFailures++
                    if (groundingFailures >= MAX_GROUNDING_FAILURES) {
                        return VariantOutcome.Suppressed(
                            SuppressedVariant(
                                strategy,
                                "Invented ${ungrounded.joinToString(", ")} twice; nothing shipped",
                                report,
                            ),
                        )
                    }
                    constraints += "Do not mention: ${ungrounded.joinToString(", ")}."
                }

                escalation != null -> {
                    constraints += "Do not suggest meeting up or anything physical."
                }

                style.isEmpty() -> return VariantOutcome.Shipped(draft)

                else -> {
                    // §7.1: keep the closest candidate to ship with a badge.
                    if (bestStyleCandidate == null ||
                        style.size < bestStyleCandidate.gates.styleDeviations.size
                    ) {
                        bestStyleCandidate = draft
                    }
                }
            }
        }

        return bestStyleCandidate
            ?.let { VariantOutcome.Shipped(it) }
            ?: VariantOutcome.Suppressed(
                SuppressedVariant(
                    strategy,
                    "No candidate passed the gates in $MAX_ATTEMPTS attempts",
                    GateReport(attempts = MAX_ATTEMPTS),
                ),
            )
    }

    /**
     * §6.4. One retry for any variant that collapsed into a synonym of an
     * earlier one, at a higher temperature and with the offending draft quoted
     * as something to avoid.
     */
    private suspend fun enforceDistinctness(
        context: CapturedContext,
        stage: ConversationStage,
        shipped: MutableList<Draft>,
    ): Long {
        val flagged = Distinctness.tooSimilar(shipped.map { it.text })
        var extraMs = 0L
        flagged.forEach { position ->
            val original = shipped[position]
            val others = shipped.filterIndexed { i, _ -> i != position }.map { it.text }
            val outcome = attemptVariant(
                context = context,
                stage = stage,
                strategy = original.strategy,
                initialConstraints = listOf(
                    "Take a different angle from these, and do not reuse their words: " +
                        others.joinToString(" | "),
                ),
                seedOffset = DISTINCTNESS_SEED_OFFSET,
            )
            if (outcome is VariantOutcome.Shipped) {
                shipped[position] = outcome.draft
                extraMs += outcome.draft.inferenceMs
            }
            // §6.4 caps this at one retry. A variant that is still too close
            // ships anyway — three options where two rhyme beats two options.
        }
        return extraMs
    }

    /** §6.5, always offered; leads on the weakest tiers. */
    private fun templateDraft(context: CapturedContext, stage: ConversationStage): Draft? {
        if (stage != ConversationStage.OPENER) return null
        val detail = TemplatePath.pickDetail(context.profile) ?: return null
        val pattern = openerPatterns.firstOrNull() ?: return null

        val filled = TemplatePath.fill(pattern, detail)
        val compiled = compiler.compile(filled)
        if (compiled.needsRegeneration) return null

        val report = GateReport(
            styleDeviations = verifier.verify(compiled.text),
            ungroundedTerms = grounding.check(compiled.text, context),
            escalationViolation = EscalationGate.check(compiled.text, stage, context),
            attempts = 1,
        )
        if (!report.shippable) return null

        return toDraft(
            context = context,
            strategy = Strategy.TEMPLATE_OPENER,
            raw = filled,
            compiled = compiled,
            report = report,
            inferenceMs = 0L,
        )
    }

    private fun retrieveExamples(context: CapturedContext, stage: ConversationStage): List<SentMessage> {
        val query = listOfNotNull(
            context.theirMessages.lastOrNull()?.text,
            context.profile.prompts.firstOrNull()?.answer,
            context.profile.bio,
        ).joinToString(" ")
        return index.search(query, limit = PromptBuilder.MAX_EXAMPLES, stage = stage)
            .map { it.message }
    }

    private fun toDraft(
        context: CapturedContext,
        strategy: Strategy,
        raw: String,
        compiled: CompiledDraft,
        report: GateReport,
        inferenceMs: Long,
    ) = Draft(
        id = "${context.conversationId}:${strategy.name}",
        conversationId = context.conversationId,
        strategy = strategy,
        rawModelOutput = raw,
        text = compiled.text,
        transformsApplied = compiled.transforms,
        gates = report,
        modelTier = engine?.tier ?: ModelTier.TEMPLATE_ONLY,
        inferenceMs = inferenceMs,
        createdAt = clock(),
    )

    /**
     * A stable seed per strategy and attempt.
     *
     * Stable so that regenerating the same variant twice does not silently
     * produce a different message each time the screen rotates; distinct per
     * strategy so §6.1's "different seeds" is actually true rather than three
     * calls to the same sampler.
     */
    private fun seedFor(strategy: Strategy, attempt: Int): Int =
        strategy.ordinal * PRIME + attempt

    /**
     * §12 budgets ~40 tokens per variant. The ceiling is generous against that
     * so the compiler's truncation, not the sampler's cutoff, decides where a
     * message ends — a message cut off by `maxTokens` ends mid-word.
     */
    private fun maxTokensFor(): Int = (voice.maxDraftWords * TOKENS_PER_WORD).toInt() + 8

    private companion object {
        /** §7.1: "Cap at 2 retries" — three attempts in total. */
        const val MAX_ATTEMPTS = 3

        /** §7.2: "on second failure, ship no draft for that variant". */
        const val MAX_GROUNDING_FAILURES = 2

        const val BASE_TEMPERATURE = 0.8f
        const val TEMPERATURE_STEP = 0.15f
        const val DISTINCTNESS_SEED_OFFSET = 977
        const val PRIME = 31
        const val TOKENS_PER_WORD = 1.6
    }
}
