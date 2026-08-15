package dev.sift.model

import kotlinx.serialization.Serializable

/** Router output (§6.4). */
@Serializable
enum class ContentClass {
    PORTRAIT,
    SCENE,

    /** Screenshots and documents. Grading these is nonsense; they are routed out. */
    NON_PHOTOGRAPHIC,
}

/** Which grading profile a job ran (§6.7, §6.8). */
@Serializable
enum class GradeProfile {
    PORTRAIT,
    SCENE,

    /** Explicitly skipped — non-photographic, or the user marked DO_NOT_GRADE. */
    NONE,
    ;

    companion object {
        fun forContentClass(contentClass: ContentClass): GradeProfile = when (contentClass) {
            ContentClass.PORTRAIT -> PORTRAIT
            ContentClass.SCENE -> SCENE
            ContentClass.NON_PHOTOGRAPHIC -> NONE
        }
    }
}

/** Triage verdict (§8). */
@Serializable
enum class Verdict { KEEP, TOSS, SKIP }

/**
 * The single source of truth for where an asset sits in its life (§9.1).
 *
 * Every transition is written to Room *before* any filesystem operation, so a
 * process death mid-transition is recoverable rather than ambiguous.
 */
@Serializable
enum class LifecycleState {
    UNTRIAGED,

    /** Tossed at triage. Deletion batch 1 — trashed immediately on commit. */
    TRASHED_AT_TRIAGE,
    QUEUED_FOR_GRADE,
    GRADING,

    /** Graded, output written, waiting for the user to look at it. */
    PENDING_REVIEW,

    /** User approved the graded result. The original is now eligible for batch 2. */
    APPROVED,

    /** Deletion batch 2 complete. Terminal. */
    ORIGINAL_TRASHED,

    /** Graded result rejected. The graded file is discarded; the original is kept. */
    REJECTED,

    /** Right as shot. Never offered for grading again. */
    DO_NOT_GRADE,

    /** Decode failed (§12). Skipped, batch continues. */
    UNREADABLE,
}

@Serializable
enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

/**
 * The only tuning signal the app gets (§9.5).
 *
 * A run of [TOO_WARM] across many rejections means the portrait b* target is
 * high for this user's typical lighting, and one number moves instead of a guess.
 */
@Serializable
enum class RejectionReason {
    TOO_WARM,
    TOO_COOL,
    TOO_CONTRASTY,
    TOO_FLAT,
    LOST_DETAIL,
    SKIN_WRONG,
    PREFER_ORIGINAL,
}

/** Inline recovery offered alongside a rejection (§9.5). */
@Serializable
enum class RegradeAction {
    /** Fixes router misclassification: portrait ↔ scene. */
    OTHER_PROFILE,

    /** All adaptive amounts × 0.5. */
    REDUCED_STRENGTH,

    /** Some frames are right as shot. */
    KEEP_ORIGINAL,
}
