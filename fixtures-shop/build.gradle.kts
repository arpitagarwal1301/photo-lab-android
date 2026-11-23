plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "dev.spikeai.fixtures.shop"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(project(":guard-core"))
    testImplementation(libs.preview.scanner.android)
    testImplementation(libs.junit)
}

// Collects per-screen findings written by the tests and scores them against the planted bugs.
val guardReport = tasks.register("guardReport") {
    val guardDir = layout.buildDirectory.dir("guard")
    val expectedFile = file("src/test/resources/expected-bugs.txt")
    doLast {
        val dir = guardDir.get().asFile
        val screens = dir.resolve("screens").listFiles { f -> f.name.endsWith(".findings.tsv") }
            ?.sortedBy { it.name }.orEmpty()
        val findings = screens.flatMap { f -> f.readLines().filter { it.isNotBlank() }.map { it.split('\t') } }
        val found = findings.map { "${it[0]} ${it[1]}" }.toSet()
        val expected = expectedFile.readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val caught = expected intersect found
        val missed = expected - found
        val falseAlarms = found - expected

        val report = buildString {
            appendLine("# Commerce PR Guard — report")
            appendLine()
            appendLine("- Screens checked: ${screens.size}")
            appendLine("- Planted bugs caught: ${caught.size}/${expected.size}")
            appendLine("- False alarms: ${falseAlarms.size}")
            appendLine("- LLM calls: 0")
            appendLine()
            appendLine("## Findings")
            findings.forEach { appendLine("- **${it[0]}** `${it[1]}` — ${it[2]}") }
            if (missed.isNotEmpty()) { appendLine(); appendLine("## Missed"); missed.forEach { appendLine("- $it") } }
            if (falseAlarms.isNotEmpty()) { appendLine(); appendLine("## False alarms"); falseAlarms.forEach { appendLine("- $it") } }
        }
        dir.resolve("REPORT.md").writeText(report)
        println(report)
    }
}

tasks.withType<Test>().configureEach {
    doFirst { layout.buildDirectory.dir("guard/screens").get().asFile.deleteRecursively() }
    finalizedBy(guardReport)
}
