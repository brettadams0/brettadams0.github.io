pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cue"

// ---------------------------------------------------------------------------
// Pure-JVM modules.
//
// Everything that decides draft quality lives here: the voice profiler and
// compiler (§4), bubble attribution (§4.2), BM25 retrieval (§4.3), stage
// classification (§6.3), and the quality gates (§7). None of it needs an
// Android SDK, a device, or a model file, which is deliberate — §16 lists
// eleven traps and nine of them are logic bugs that a JVM test can catch in
// milliseconds. The model is asked for content and nothing else, so almost
// nothing that matters requires inference to test.
// ---------------------------------------------------------------------------
include(":core:model")
include(":core:voice")
include(":core:capture")
include(":core:draft")
include(":core:testing")

// ---------------------------------------------------------------------------
// Android modules. Included only when an SDK is actually present, so that
// `gradle :core:voice:test` works on a machine that has never installed one.
// Without this guard the Android Gradle Plugin fails at configuration time
// ("SDK location not found") and takes the pure-JVM modules down with it.
// ---------------------------------------------------------------------------
val androidSdkPresent =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkPresent) {
    include(":core:data")
    include(":core:inference")
    include(":app")
} else {
    logger.lifecycle(
        "cue: no Android SDK detected — configuring JVM modules only " +
            "(:core:model, :core:voice, :core:capture, :core:draft, :core:testing). " +
            "Set ANDROID_HOME or add local.properties to build the app.",
    )
}
