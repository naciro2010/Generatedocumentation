package io.docgen.plugins.impl

import io.docgen.core.domain.Evidence
import io.docgen.core.model.*
import io.docgen.parsing.extractor.RegexCodeExtractor
import io.docgen.plugins.api.AnalysisPlugin
import io.docgen.plugins.api.FunctionalInsight
import io.docgen.plugins.api.FunctionalRule
import io.docgen.plugins.api.PluginAnalysisResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Plugin pour projets Node.js/Express.
 */
class NodeExpressPlugin : AnalysisPlugin {
    override val name = "node-express"
    override val description = "Analyze Node.js projects using Express framework"

    private val extractor = RegexCodeExtractor()

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        // Check for JavaScript/TypeScript
        if (fingerprint.languages.containsKey("JavaScript") || fingerprint.languages.containsKey("TypeScript")) {
            score += 0.3
        }

        // Check for Express in dependencies
        if (fingerprint.frameworks.containsKey("Node.js")) {
            score += 0.3
        }

        // Check package.json for express
        val packageJson = projectPath.resolve("package.json")
        if (Files.exists(packageJson)) {
            val content = packageJson.readText()
            if (content.contains("\"express\"")) {
                score += 0.4
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()

        // Find route definitions
        Files.walk(projectPath)
            .filter { it.extension in listOf("js", "ts") }
            .filter { !it.toString().contains("/node_modules/") }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Detect Express routes
                val routePattern = Regex("""(app|router)\.(get|post|put|patch|delete)\s*\(\s*['"]([^'"]+)['"]""")
                routePattern.findAll(content).forEach { match ->
                    val method = match.groupValues[2].uppercase()
                    val path = match.groupValues[3]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    endpoints.add(
                        EndpointIR(
                            id = "$relativePath:$lineNum",
                            method = HttpMethod.valueOf(method.uppercase()),
                            path = path,
                            handler = "$relativePath:$lineNum",
                            evidences = listOf(
                                Evidence(
                                    filePath = relativePath,
                                    startLine = lineNum,
                                    endLine = lineNum,
                                    symbol = "route:$method:$path",
                                    snippet = match.value
                                )
                            )
                        )
                    )

                    // Generate functional insight
                    insights.add(
                        FunctionalInsight(
                            category = "feature",
                            title = "API Endpoint: $method $path",
                            description = "Exposes HTTP endpoint for $path",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
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
                languages = mapOf("JavaScript" to 1.0),
                frameworks = mapOf("Express" to 1.0),
                buildTools = mapOf("npm" to "package.json"),
                detectedFiles = listOf("package.json")
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
                name = "express-middleware",
                pattern = Regex("""app\.use\("""),
                category = "architecture",
                description = "Express middleware detected"
            ),
            FunctionalRule(
                name = "authentication",
                pattern = Regex("""(passport|jwt|auth)""", RegexOption.IGNORE_CASE),
                category = "security",
                description = "Authentication mechanism detected"
            )
        )
    }
}
