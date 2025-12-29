rootProject.name = "universal-doc-generator"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    // Temporarily exclude apps - they need Spring Boot
    // "apps:api",
    // "apps:worker",
    "libs:core",
    "libs:parsing",
    "libs:plugins",
    "libs:llm",
    "libs:docs",
    "libs:graph-neo4j"
)
