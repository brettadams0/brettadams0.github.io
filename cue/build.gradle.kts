plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// ---------------------------------------------------------------------------
// §2.1 — draft-only is an architectural property, not a policy.
//
// §14.6 asks for a static check that fails the build if `performAction` appears
// anywhere, and §16 trap 11 explains why: one call turns a drafting tool into a
// bot operating the account, and dating-app bans are effectively permanent and
// often device-linked. A code-review habit is not enough for a rule whose
// violation cannot be undone.
//
// The ban list is wider than the spec's single name because `performAction` is
// not the only way to drive the UI. Everything here writes to a surface Cue
// does not own.
// ---------------------------------------------------------------------------
val forbiddenAutomationApis = listOf(
    "performAction",
    "performGlobalAction",
    "dispatchGesture",
    "ACTION_SET_TEXT",
    "ACTION_PASTE",
    "ACTION_CLICK",
    "ACTION_IME_ENTER",
)

val verifyDraftOnly = tasks.register("verifyDraftOnly") {
    group = "verification"
    description = "Fails the build if any code path could act on the screen (§2.1, §14.6)."

    val sources = fileTree(rootDir) {
        include("**/src/**/*.kt", "**/src/**/*.java")
        exclude("**/build/**")
    }
    inputs.files(sources)
    // The task's own verdict is its output; without this Gradle re-runs it every
    // time, which is fine but noisy in `--console=plain` logs.
    outputs.upToDateWhen { false }

    doLast {
        val hits = mutableListOf<String>()
        sources.forEach { file ->
            var inBlockComment = false
            file.readLines().forEachIndexed { index, raw ->
                // Strip comments before matching. The rule bans *calls*, and the
                // architecture is worth explaining in prose — a comment saying
                // "performAction is never called" should not fail the build.
                val line = stripComments(raw, inBlockComment).also {
                    inBlockComment = it.second
                }.first
                forbiddenAutomationApis.forEach { api ->
                    if (line.contains(api)) {
                        hits += "${file.relativeTo(rootDir)}:${index + 1}: $api — ${raw.trim()}"
                    }
                }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "Cue is draft-only by construction (§2.1). These call sites would act on " +
                    "the screen:\n" + hits.joinToString("\n") +
                    "\n\nCue reads to draft. It never operates the account. Copy-to-clipboard " +
                    "is terminal — there is no supported way to add a send path.",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// §2.3 — nothing leaves the device, at all.
//
// The app ships without the INTERNET permission. That claim is only worth
// making if it is enforced: a transitive dependency can merge the permission
// into the final manifest without anyone editing a file. This checks the
// sources; `verifyMergedManifestOffline` in :app checks the merged result,
// which is the one that actually ships.
// ---------------------------------------------------------------------------
val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Fails the build if a source manifest declares INTERNET (§2.3)."

    val manifests = fileTree(rootDir) {
        include("**/src/**/AndroidManifest.xml")
        exclude("**/build/**")
    }
    inputs.files(manifests)
    outputs.upToDateWhen { false }

    doLast {
        val hits = manifests.filter { it.readText().contains("android.permission.INTERNET") }
            .map { it.relativeTo(rootDir).toString() }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "§2.3: the app ships with no INTERNET permission. Declared in:\n" +
                    hits.joinToString("\n") +
                    "\n\nModel download is a separate flow (§3.3), not a permission the " +
                    "drafting app holds.",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The extension's content script is the only code with access to the Tinder
// page, so it is the only place a write could originate (§2.1, §5.3). The side
// panel writes to its own DOM constantly and is not scanned.
// ---------------------------------------------------------------------------
val verifyContentScriptReadOnly = tasks.register("verifyContentScriptReadOnly") {
    group = "verification"
    description = "Fails the build if the extension's content script can write to the page (§5.3)."

    val contentScripts = fileTree(rootDir) {
        include("extension/src/content/**/*.js")
    }
    inputs.files(contentScripts)
    outputs.upToDateWhen { false }

    val forbiddenDomWrites = listOf(
        "execCommand",
        "KeyboardEvent",
        "InputEvent",
        "dispatchEvent",
        ".click(",
        ".focus(",
        "innerHTML",
        "setNativeValue",
    )

    doLast {
        val hits = mutableListOf<String>()
        contentScripts.forEach { file ->
            var inBlockComment = false
            file.readLines().forEachIndexed { index, raw ->
                val line = stripComments(raw, inBlockComment).also {
                    inBlockComment = it.second
                }.first
                forbiddenDomWrites.forEach { api ->
                    if (line.contains(api)) {
                        hits += "${file.relativeTo(rootDir)}:${index + 1}: $api — ${raw.trim()}"
                    }
                }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "The content script reads the DOM and reports it. It does not write to it " +
                    "(§5.3):\n" + hits.joinToString("\n"),
            )
        }
    }
}

val verifyArchitecturalInvariants = tasks.register("verifyArchitecturalInvariants") {
    group = "verification"
    description = "Every structural guarantee in §2, checked rather than trusted."
    dependsOn(verifyDraftOnly, verifyNoInternetPermission, verifyContentScriptReadOnly)
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyArchitecturalInvariants)
    }
}

/**
 * Returns [line] with comment content removed, plus whether a block comment is
 * still open at the end of it.
 *
 * Deliberately simple: it does not understand string literals containing comment
 * markers. That direction of error is the safe one — a banned name inside a
 * string would still be reported.
 */
fun stripComments(line: String, startsInBlockComment: Boolean): Pair<String, Boolean> {
    val out = StringBuilder()
    var inBlock = startsInBlockComment
    var i = 0
    while (i < line.length) {
        if (inBlock) {
            if (line.startsWith("*/", i)) {
                inBlock = false
                i += 2
            } else {
                i++
            }
        } else {
            if (line.startsWith("/*", i)) {
                inBlock = true
                i += 2
            } else if (line.startsWith("//", i)) {
                break
            } else {
                out.append(line[i])
                i++
            }
        }
    }
    return out.toString() to inBlock
}
