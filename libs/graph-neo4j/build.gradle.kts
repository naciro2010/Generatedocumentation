plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Neo4j Java Driver (without Spring)
    implementation("org.neo4j.driver:neo4j-java-driver:5.13.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.testcontainers:neo4j:1.19.3")
}
