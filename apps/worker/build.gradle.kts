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

    // Spring Boot (no web, just scheduling & actuator)
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Git operations
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    // Archive handling
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.bootJar {
    archiveFileName.set("universal-doc-generator-worker.jar")
}
