plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))
    api(project(":libs:parsing"))

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
