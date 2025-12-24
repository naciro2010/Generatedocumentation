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
 * Plugin pour projets Go.
 */
class GoPlugin : AnalysisPlugin {
    override val name = "golang"
    override val description = "Analyze Go projects"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Go")) {
            score += 0.5
        }

        val goMod = projectPath.resolve("go.mod")
        if (Files.exists(goMod)) {
            score += 0.5
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()

        Files.walk(projectPath)
            .filter { it.extension == "go" }
            .filter { !it.toString().contains("/vendor/") }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Detect HTTP handlers (common patterns)
                // Pattern 1: http.HandleFunc
                val handleFuncPattern = Regex("""http\.HandleFunc\(['""]([^'"]+)['""]\s*,\s*(\w+)\)""")
                handleFuncPattern.findAll(content).forEach { match ->
                    val path = match.groupValues[1]
                    val handler = match.groupValues[2]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    endpoints.add(
                        EndpointIR(
                            id = "$relativePath:$lineNum",
                            method = HttpMethod.GET, // Default, could be multiple
                            path = path,
                            handler = handler,
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = handler
                                )
                            )
                        )
                    )

                    insights.add(
                        FunctionalInsight(
                            category = "feature",
                            title = "HTTP Handler: $path",
                            description = "Go HTTP handler",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Pattern 2: Gin framework (router.GET/POST/etc.)
                val ginPattern = Regex("""router\.(GET|POST|PUT|PATCH|DELETE)\(['""]([^'"]+)['""]\s*,\s*(\w+)\)""")
                ginPattern.findAll(content).forEach { match ->
                    val method = match.groupValues[1]
                    val path = match.groupValues[2]
                    val handler = match.groupValues[3]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    endpoints.add(
                        EndpointIR(
                            id = "$relativePath:$lineNum",
                            method = HttpMethod.valueOf(method),
                            path = path,
                            handler = handler,
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = handler
                                )
                            )
                        )
                    )

                    insights.add(
                        FunctionalInsight(
                            category = "feature",
                            title = "API Endpoint: $method $path",
                            description = "Gin framework endpoint",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Detect structs (like entities)
                val structPattern = Regex("""type\s+(\w+)\s+struct\s*\{""")
                structPattern.findAll(content).forEach { match ->
                    val structName = match.groupValues[1]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    services.add(
                        ServiceIR(
                            id = structName,
                            name = structName,
                            fqn = "$relativePath.$structName",
                            type = "struct",
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = structName
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
            services = services,
            endpoints = endpoints
        )

        val projectIR = ProjectIR(
            projectId = projectPath.fileName.toString(),
            name = projectPath.fileName.toString(),
            fingerprint = ProjectFingerprint(
                languages = mapOf("Go" to 1.0),
                frameworks = emptyMap(),
                buildTools = mapOf("go" to "go.mod"),
                detectedFiles = listOf("go.mod")
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
                name = "jwt-auth",
                pattern = Regex("""jwt""", RegexOption.IGNORE_CASE),
                category = "security",
                description = "JWT authentication"
            ),
            FunctionalRule(
                name = "gorm-database",
                pattern = Regex("""gorm"""),
                category = "data-access",
                description = "GORM ORM usage"
            )
        )
    }
}
