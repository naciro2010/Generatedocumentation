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
 * Plugin pour projets Rust (Actix-web, Rocket).
 */
class RustPlugin : AnalysisPlugin {
    override val name = "rust"
    override val description = "Analyze Rust projects (Actix-web, Rocket)"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Rust")) {
            score += 0.5
        }

        val cargoToml = projectPath.resolve("Cargo.toml")
        if (Files.exists(cargoToml)) {
            val content = cargoToml.readText()
            if (content.contains("actix-web") || content.contains("rocket")) {
                score += 0.5
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()

        Files.walk(projectPath)
            .filter { it.extension == "rs" }
            .filter { !it.toString().contains("/target/") }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Actix-web routes: #[get("/path")]
                val actixPattern = Regex("""#\[(get|post|put|patch|delete)\(['""]([^'"]+)['""]\)\]\s+(?:pub\s+)?(?:async\s+)?fn\s+(\w+)""")
                actixPattern.findAll(content).forEach { match ->
                    val method = match.groupValues[1].uppercase()
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
                            description = "Actix-web endpoint",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Rocket routes: #[get("/path")]
                val rocketPattern = Regex("""#\[(get|post|put|patch|delete)\(['""]([^'"]+)['""]\)\]\s+fn\s+(\w+)""")
                rocketPattern.findAll(content).forEach { match ->
                    val method = match.groupValues[1].uppercase()
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
                            description = "Rocket endpoint",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Detect structs
                val structPattern = Regex("""(?:pub\s+)?struct\s+(\w+)""")
                structPattern.findAll(content).forEach { match ->
                    val structName = match.groupValues[1]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    services.add(
                        ServiceIR(
                            id = structName,
                            name = structName,
                            fqn = "$relativePath::$structName",
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
                languages = mapOf("Rust" to 1.0),
                frameworks = mapOf("actix-web/rocket" to 1.0),
                buildTools = mapOf("cargo" to "Cargo.toml"),
                detectedFiles = listOf("Cargo.toml")
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
                name = "serde-serialization",
                pattern = Regex("""#\[derive\(.*Serialize.*\)\]"""),
                category = "serialization",
                description = "Serde serialization"
            ),
            FunctionalRule(
                name = "diesel-orm",
                pattern = Regex("""diesel"""),
                category = "data-access",
                description = "Diesel ORM"
            )
        )
    }
}
