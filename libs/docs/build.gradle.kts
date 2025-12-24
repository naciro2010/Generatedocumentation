plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Template engine (for Markdown generation)
    implementation("org.freemarker:freemarker:2.3.33")

    // Markdown processing
    implementation("org.commonmark:commonmark:0.22.0")

    // YAML for MkDocs config
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.13")
}
