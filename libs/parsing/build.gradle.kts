plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // JNA for native libraries
    implementation("net.java.dev.jna:jna:5.13.0")

    // Text & pattern matching
    implementation("org.apache.commons:commons-text:1.10.0")

    // YAML/JSON parsing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
