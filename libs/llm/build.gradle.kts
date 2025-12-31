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

    // AWS SDK for Bedrock (optional - commented out for now due to version conflicts)
    // To use: uncomment and update version according to AWS SDK releases
    // implementation("software.amazon.awssdk:bedrock-runtime:2.26.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
