plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.sift.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 30 // MediaStore.createTrashRequest is API 30+ (§8)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            // §4.2 — export schemas and commit them, so migrations are explicit.
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // §3 — OpenCV across all ABIs would be ~40MB; arm64 is the only ABI the
    // target device needs. Kept even though the pipeline is now pure Kotlin,
    // because ONNX Runtime would reintroduce native code if M8 ever lands.
    ndk {
        abiFilters += "arm64-v8a"
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:imaging"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.room.testing)
}
