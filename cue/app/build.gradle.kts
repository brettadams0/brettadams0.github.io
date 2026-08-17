plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.cue.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.cue.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:voice"))
    implementation(project(":core:capture"))
    implementation(project(":core:draft"))
    implementation(project(":core:data"))
    implementation(project(":core:inference"))

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
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

/**
 * §2.3, checked on the artefact that actually ships.
 *
 * The root project's `verifyNoInternetPermission` reads the source manifests,
 * which is necessary and not sufficient: any dependency can merge `INTERNET`
 * into the final manifest without a line changing in this repository. ML Kit and
 * MediaPipe both ship analytics-adjacent code, so this is not hypothetical.
 */
androidComponents {
    onVariants { variant ->
        val checkTask = tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}ManifestOffline") {
            group = "verification"
            description = "Fails if the merged manifest grants INTERNET (§2.3)."
            val manifests = variant.artifacts.get(
                com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST,
            )
            inputs.file(manifests)
            doLast {
                val text = manifests.get().asFile.readText()
                val offenders = listOf(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                ).filter { text.contains(it) }
                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "The merged manifest grants ${offenders.joinToString()}. A dependency " +
                            "added it. Find it with `gradlew :app:processDebugMainManifest --info` " +
                            "and add a tools:node=\"remove\" override, or drop the dependency.\n\n" +
                            "\"What leaves your device: nothing\" (§10) has to be true of the APK, " +
                            "not of the source.",
                    )
                }
            }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkTask) }
    }
}
