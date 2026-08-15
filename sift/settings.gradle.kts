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

rootProject.name = "sift"

// ---------------------------------------------------------------------------
// Pure-JVM modules. These carry the imaging pipeline (§2, §6) and the shared
// types it operates on. They build and test without an Android SDK, which is
// the point: §4.1 requires :core:imaging to be exercisable in isolation, and
// the module that determines output quality should not need a device attached
// to run its test suite.
// ---------------------------------------------------------------------------
include(":core:model")
include(":core:imaging")
include(":core:testing")

// ---------------------------------------------------------------------------
// Android modules. Included only when an SDK is actually present, so that
// `gradle :core:imaging:test` works on a machine that has never installed one.
// Without this guard the Android Gradle Plugin fails at configuration time
// ("SDK location not found") and takes the pure-JVM modules down with it.
// ---------------------------------------------------------------------------
val androidSdkPresent =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkPresent) {
    include(":core:data")
    include(":core:ml")
    include(":app")
} else {
    logger.lifecycle(
        "sift: no Android SDK detected — configuring JVM modules only " +
            "(:core:model, :core:imaging, :core:testing). " +
            "Set ANDROID_HOME or add local.properties to build the app.",
    )
}
