import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":embed-core"))
    implementation(project(":search-core"))
    implementation(libs.onnxruntime.jvm)
}

// ./gradlew :bench:run  → results/m1-benchmark.md
tasks.register<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.spikeai.bench.BenchKt")
    maxHeapSize = "4g"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(listOf(rootProject.file(".work").absolutePath, rootProject.file("results").absolutePath) + (project.findProperty("mode")?.toString()?.let { listOf(it) } ?: emptyList()))
}
