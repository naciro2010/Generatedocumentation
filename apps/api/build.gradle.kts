plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Internal libs
    implementation(project(":libs:core"))
    implementation(project(":libs:parsing"))
    implementation(project(":libs:plugins"))
    implementation(project(":libs:llm"))
    implementation(project(":libs:docs"))

    // Spring Boot - Minimal
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.bootJar {
    archiveFileName.set("universal-doc-generator-api.jar")
}
