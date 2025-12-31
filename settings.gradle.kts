rootProject.name = "universal-doc-generator"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "libs:core",
    "libs:parsing",
    "libs:plugins",
    "libs:llm",
    "libs:docs"
)
