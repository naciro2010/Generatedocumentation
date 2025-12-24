package io.docgen.plugins.impl

import io.docgen.core.domain.Evidence
import io.docgen.core.model.*
import io.docgen.plugins.api.AnalysisPlugin
import io.docgen.plugins.api.FunctionalInsight
import io.docgen.plugins.api.FunctionalRule
import io.docgen.plugins.api.PluginAnalysisResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Plugin pour projets Python/FastAPI.
 */
class PythonFastAPIPlugin : AnalysisPlugin {
    override val name = "python-fastapi"
    override val description = "Analyze Python projects using FastAPI framework"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Python")) {
            score += 0.4
        }

        // Check requirements.txt or pyproject.toml for fastapi
        listOf("requirements.txt", "pyproject.toml", "Pipfile").forEach { fileName ->
            val file = projectPath.resolve(fileName)
            if (Files.exists(file)) {
                val content = file.readText()
                if (content.contains("fastapi", ignoreCase = true)) {
                    score += 0.6
                    return@forEach
                }
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val entities = mutableListOf<EntityIR>()
        val insights = mutableListOf<FunctionalInsight>()

        Files.walk(projectPath)
            .filter { it.extension == "py" }
            .filter { !it.toString().contains("/__pycache__/") }
            .filter { !it.toString().contains("/venv/") }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Detect FastAPI routes
                val routePattern = Regex("""@app\.(get|post|put|patch|delete)\(['"]([^'"]+)['"]\)""")
                routePattern.findAll(content).forEach { match ->
                    val method = match.groupValues[1].uppercase()
                    val path = match.groupValues[2]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    endpoints.add(
                        EndpointIR(
                            id = "$relativePath:$lineNum",
                            method = HttpMethod.valueOf(method),
                            path = path,
                            handler = "$relativePath:$lineNum",
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = "route:$method:$path"
                                )
                            )
                        )
                    )

                    insights.add(
                        FunctionalInsight(
                            category = "feature",
                            title = "API Endpoint: $method $path",
                            description = "FastAPI endpoint handling $method requests",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Detect Pydantic models (entities)
                val modelPattern = Regex("""class\s+(\w+)\(BaseModel\)""")
                modelPattern.findAll(content).forEach { match ->
                    val className = match.groupValues[1]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    entities.add(
                        EntityIR(
                            id = className,
                            name = className,
                            fqn = "$relativePath.$className",
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = className
                                )
                            )
                        )
                    )
                }
            }

        val module = ModuleIR(
            id = "main",
            name = "main",
            path = ".",
            type = ModuleType.API,
            endpoints = endpoints,
            entities = entities
        )

        val projectIR = ProjectIR(
            projectId = projectPath.fileName.toString(),
            name = projectPath.fileName.toString(),
            fingerprint = ProjectFingerprint(
                languages = mapOf("Python" to 1.0),
                frameworks = mapOf("FastAPI" to 1.0),
                buildTools = mapOf("pip" to "requirements.txt"),
                detectedFiles = listOf("requirements.txt")
            ),
            modules = listOf(module)
        )

        return PluginAnalysisResult(
            projectIR = projectIR,
            functionalInsights = insights
        )
    }

    override fun functionalRules(): List<FunctionalRule> {
        return listOf(
            FunctionalRule(
                name = "pydantic-validation",
                pattern = Regex("""class\s+\w+\(BaseModel\)"""),
                category = "validation",
                description = "Pydantic model with validation"
            ),
            FunctionalRule(
                name = "dependency-injection",
                pattern = Regex("""Depends\("""),
                category = "architecture",
                description = "FastAPI dependency injection"
            )
        )
    }
}
