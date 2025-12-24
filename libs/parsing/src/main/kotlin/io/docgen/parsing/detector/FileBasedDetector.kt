package io.docgen.parsing.detector

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Détecteur basé sur les fichiers manifests et extensions.
 */
class FileBasedDetector : LanguageDetector {

    private val manifestRules = mapOf(
        "package.json" to DetectionRule("JavaScript", 0.9, frameworks = listOf("Node.js"), buildTool = "npm"),
        "yarn.lock" to DetectionRule("JavaScript", 0.5, buildTool = "yarn"),
        "pnpm-lock.yaml" to DetectionRule("JavaScript", 0.5, buildTool = "pnpm"),
        "tsconfig.json" to DetectionRule("TypeScript", 0.9),
        "deno.json" to DetectionRule("TypeScript", 0.8, frameworks = listOf("Deno")),

        "pom.xml" to DetectionRule("Java", 0.9, buildTool = "maven"),
        "build.gradle" to DetectionRule("Java", 0.8, buildTool = "gradle"),
        "build.gradle.kts" to DetectionRule("Kotlin", 0.9, buildTool = "gradle"),

        "requirements.txt" to DetectionRule("Python", 0.7),
        "setup.py" to DetectionRule("Python", 0.8),
        "pyproject.toml" to DetectionRule("Python", 0.9),
        "Pipfile" to DetectionRule("Python", 0.8, buildTool = "pipenv"),
        "poetry.lock" to DetectionRule("Python", 0.8, buildTool = "poetry"),

        "go.mod" to DetectionRule("Go", 0.95, buildTool = "go-modules"),
        "go.sum" to DetectionRule("Go", 0.7),

        "Cargo.toml" to DetectionRule("Rust", 0.95, buildTool = "cargo"),
        "Cargo.lock" to DetectionRule("Rust", 0.7),

        "composer.json" to DetectionRule("PHP", 0.9, buildTool = "composer"),

        "Gemfile" to DetectionRule("Ruby", 0.9, buildTool = "bundler"),

        ".csproj" to DetectionRule("C#", 0.9, buildTool = "dotnet"),
        "*.csproj" to DetectionRule("C#", 0.9),

        "mix.exs" to DetectionRule("Elixir", 0.95, buildTool = "mix")
    )

    private val extensionRules = mapOf(
        "kt" to "Kotlin",
        "kts" to "Kotlin",
        "java" to "Java",
        "py" to "Python",
        "js" to "JavaScript",
        "jsx" to "JavaScript",
        "ts" to "TypeScript",
        "tsx" to "TypeScript",
        "go" to "Go",
        "rs" to "Rust",
        "rb" to "Ruby",
        "php" to "PHP",
        "cs" to "C#",
        "ex" to "Elixir",
        "exs" to "Elixir"
    )

    override fun detect(projectPath: Path): DetectionResult {
        val languages = mutableMapOf<String, Double>()
        val frameworks = mutableMapOf<String, Double>()
        val buildTools = mutableMapOf<String, String>()
        val files = mutableListOf<String>()

        // Scan manifest files
        manifestRules.forEach { (fileName, rule) ->
            val foundFiles = findFiles(projectPath, fileName)
            foundFiles.forEach { file ->
                languages[rule.language] = (languages[rule.language] ?: 0.0) + rule.confidence
                rule.frameworks.forEach { fw ->
                    frameworks[fw] = (frameworks[fw] ?: 0.0) + rule.confidence
                }
                rule.buildTool?.let { buildTools[rule.language] = it }
                files.add(file.toString())
            }
        }

        // Scan file extensions
        val extensionCounts = countExtensions(projectPath)
        extensionCounts.forEach { (ext, count) ->
            extensionRules[ext]?.let { lang ->
                languages[lang] = (languages[lang] ?: 0.0) + (count * 0.01)
            }
        }

        return DetectionResult(
            languages = languages,
            frameworks = frameworks,
            buildTools = buildTools,
            files = files
        )
    }

    private fun findFiles(root: Path, pattern: String): List<Path> {
        if (!Files.exists(root)) return emptyList()

        return Files.walk(root, 3)
            .filter { Files.isRegularFile(it) }
            .filter {
                if (pattern.contains("*")) {
                    it.name.matches(Regex(pattern.replace("*", ".*")))
                } else {
                    it.name == pattern
                }
            }
            .toList()
    }

    private fun countExtensions(root: Path): Map<String, Int> {
        if (!Files.exists(root)) return emptyMap()

        return Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { !it.toString().contains("/.") } // Ignore hidden dirs
            .filter { !it.toString().contains("/node_modules/") }
            .filter { !it.toString().contains("/target/") }
            .filter { !it.toString().contains("/build/") }
            .mapNotNull { it.extension.takeIf { ext -> ext.isNotEmpty() } }
            .toList()
            .groupingBy { it }
            .eachCount()
    }
}

data class DetectionRule(
    val language: String,
    val confidence: Double,
    val frameworks: List<String> = emptyList(),
    val buildTool: String? = null
)
