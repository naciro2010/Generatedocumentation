plugins {
    kotlin("jvm")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:core"))

    // Neo4j
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.testcontainers:neo4j:1.20.4")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}
