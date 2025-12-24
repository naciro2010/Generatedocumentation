plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Tree-sitter (via JNA bindings)
    implementation("net.java.dev.jna:jna:5.15.0")

    // Regex & pattern matching
    implementation("org.apache.commons:commons-text:1.12.0")

    // YAML/JSON parsing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.13")
}
