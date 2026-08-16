import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // A 12MP frame is ~144MB as unbounded float RGB; the gate tests hold a
    // source and an output simultaneously.
    maxHeapSize = "3g"

    // Forwards -Dsift.bench=true into the test JVM so PipelineBenchmark can be
    // switched on from the command line. Gradle's daemon properties are not
    // inherited by the forked test process, so without this the benchmark is
    // unconditionally skipped and reports success while measuring nothing.
    System.getProperty("sift.bench")?.let { systemProperty("sift.bench", it) }

    testLogging {
        events("passed", "failed", "skipped")
        // The benchmark's only output is what it prints.
        showStandardStreams = System.getProperty("sift.bench") == "true"
    }
}
