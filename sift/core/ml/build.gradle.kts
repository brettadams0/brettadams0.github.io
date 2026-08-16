plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.sift.ml"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // No abiFilters: there is no native code in this project. §3 restricts ABIs
    // because OpenCV ships ~40MB of .so per ABI, but the imaging pipeline is pure
    // Kotlin (see core/imaging) and ONNX is not a dependency, so the APK is
    // ABI-agnostic and the filter would save nothing.
}

dependencies {
    api(project(":core:model"))
    api(project(":core:imaging"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ONNX Runtime is deliberately NOT declared. See ModelPolicy — §18 open
    // decision 3 says the §6.6 A/B must be answered before any ONNX code is
    // written, and it can delete M8 entirely.
}
