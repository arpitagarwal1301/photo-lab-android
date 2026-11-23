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
    // Same `ai.onnxruntime` API on JVM and Android: the app supplies onnxruntime-android at runtime.
    compileOnly(libs.onnxruntime.jvm)
    api(libs.kotlinx.serialization.json) // parse safetensors headers and tokenizer.json
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit)
}

tasks.test {
    systemProperty("minilm.dir", rootProject.file(".work/models/minilm").absolutePath)
    systemProperty("mobileclip.dir", rootProject.file(".work/models/mobileclip").absolutePath)
    maxHeapSize = "3g"
}
