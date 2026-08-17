plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.cue.inference"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:draft"))
    implementation(project(":core:data"))

    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)
}
