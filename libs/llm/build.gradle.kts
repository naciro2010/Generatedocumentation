plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")

    // HTTP client
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // OpenAI SDK (community)
    implementation("com.aallam.openai:openai-client:3.8.2")

    // AWS SDK for Bedrock
    implementation(platform("software.amazon.awssdk:bom:2.25.11"))
    implementation("software.amazon.awssdk:bedrock-runtime")
    implementation("software.amazon.awssdk:auth")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.13")
}
