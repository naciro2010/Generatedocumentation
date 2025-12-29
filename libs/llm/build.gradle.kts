plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // HTTP client (simple OkHttp instead of Spring WebFlux)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // OpenAI SDK (community)
    implementation("com.aallam.openai:openai-client:3.6.2")

    // AWS SDK for Bedrock (optional - can be removed if needed)
    implementation(platform("software.amazon.awssdk:bom:2.21.0"))
    implementation("software.amazon.awssdk:bedrock-runtime")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
