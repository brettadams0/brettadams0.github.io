import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing for a sideload build (§0 — never Play-distributed).
 *
 * The keystore is deliberately NOT in the repository. Committing a signing key
 * to a public repo would let anyone sign a package as `dev.sift`, and Android
 * treats same-package-plus-same-signature as a legitimate update — so a stranger's
 * build could install straight over yours. Instead `keystore.properties` is
 * gitignored and read here if present; without it the release build falls back to
 * the debug key so CI can still verify that the release variant assembles.
 *
 * Generate your own with:
 *   keytool -genkeypair -v -keystore sift-release.jks -alias sift \
 *     -keyalg RSA -keysize 4096 -validity 10000
 * then create keystore.properties with storeFile/storePassword/keyAlias/keyPassword.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "dev.sift.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sift"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v2 is what API 30+ installs from; v3 additionally supports key
                // rotation, so a lost or compromised key can be replaced later
                // without the package losing its update path.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // No abiFilters: there is no native code in this project. §3 restricts ABIs
    // because OpenCV ships ~40MB of .so per ABI, but the imaging pipeline is pure
    // Kotlin (see core/imaging) and ONNX is not a dependency, so the APK is
    // ABI-agnostic and the filter would save nothing.

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:imaging"))
    implementation(project(":core:data"))
    implementation(project(":core:ml"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.serialization.json)
}

/**
 * §3's central claim, enforced by the build.
 *
 * "No `INTERNET` permission" is the strongest thing Sift says about itself, and
 * it is exactly the kind of property that erodes silently: any dependency added
 * later can reintroduce a network permission through manifest merging, and
 * nobody would notice until they opened the app's settings page. This reads the
 * *merged* manifest — the one that actually ships — and fails the build if a
 * network permission is present.
 *
 * WAKE_LOCK and RECEIVE_BOOT_COMPLETED are allowed through: neither grants
 * network access, and both are load-bearing for §9.2's deferred foreground
 * grading (see the manifest comment).
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
)

androidComponents {
    onVariants { variant ->
        val verify = tasks.register("verifyNoNetworkPermissions${variant.name.replaceFirstChar { it.uppercase() }}") {
            group = "verification"
            description = "Fails if the merged manifest declares a network permission (§3)."
            val manifests = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifests)
            doLast {
                val text = manifests.get().asFile.readText()
                val found = forbiddenPermissions.filter { text.contains(it) }
                if (found.isNotEmpty()) {
                    throw GradleException(
                        "Merged manifest for '${variant.name}' declares ${found.joinToString()}.\n" +
                            "Sift ships with no network permission (§3). Either the dependency that " +
                            "introduced it is unnecessary, or it needs a tools:node=\"remove\" entry " +
                            "in app/src/main/AndroidManifest.xml.",
                    )
                }
            }
        }
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { c -> c.uppercase() }}" }
            .configureEach { dependsOn(verify) }
    }
}
