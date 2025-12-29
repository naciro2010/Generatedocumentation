plugins {
    kotlin("jvm")
}

dependencies {
    // JSON
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
    api("com.fasterxml.jackson.core:jackson-databind:2.15.3")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core:3.24.2")
}
