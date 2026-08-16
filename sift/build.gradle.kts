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

/**
 * Catch a comment terminator hiding inside prose.
 *
 * Writing the CIELAB axes as a pair with a slash between them embeds a comment
 * terminator, which ends a KDoc block early. Everything after it becomes code, and the
 * compiler's error points at whatever line happens to follow — never at the
 * comment. This has cost three separate debugging sessions in this repo, so it
 * is now a build failure with a message that names the actual cause.
 */
val verifyNoStrayCommentTerminators = tasks.register("verifyNoStrayCommentTerminators") {
    group = "verification"
    description = "Fails on a comment terminator embedded mid-line inside a Kotlin comment."

    val sources = fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude("**/build/**")
    }
    inputs.files(sources)

    doLast {
        // A stray terminator looks like: word character, then the terminator,
        // then more content on the same line. A legitimate one closes the block
        // and has only whitespace before it on its line.
        val offender = Regex("""\w\*/\S""")
        val hits = mutableListOf<String>()
        sources.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                if (!trimmed.startsWith("*/") && offender.containsMatchIn(line)) {
                    // Ignore string literals — the sequence inside a message is harmless.
                    val beforeQuote = line.substringBefore('"')
                    if (offender.containsMatchIn(beforeQuote)) {
                        hits += "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                    }
                }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "A comment terminator inside a comment is closing it early:\n" +
                    hits.joinToString("\n") +
                    "\n\nWrite the CIELAB axes as 'a* and b*', separated by a word.",
            )
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyNoStrayCommentTerminators)
    }
}
