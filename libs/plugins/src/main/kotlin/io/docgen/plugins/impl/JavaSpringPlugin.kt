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
 * Plugin pour projets Java/Spring Boot.
 */
class JavaSpringPlugin : AnalysisPlugin {
    override val name = "java-spring"
    override val description = "Analyze Java/Kotlin projects using Spring Boot"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Java") || fingerprint.languages.containsKey("Kotlin")) {
            score += 0.3
        }

        // Check pom.xml or build.gradle for Spring Boot
        val pomXml = projectPath.resolve("pom.xml")
        if (Files.exists(pomXml)) {
            val content = pomXml.readText()
            if (content.contains("spring-boot-starter")) {
                score += 0.7
            }
        }

        val buildGradle = projectPath.resolve("build.gradle.kts")
            .takeIf { Files.exists(it) }
            ?: projectPath.resolve("build.gradle")

        if (buildGradle != null && Files.exists(buildGradle)) {
            val content = buildGradle.readText()
            if (content.contains("spring-boot")) {
                score += 0.7
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val entities = mutableListOf<EntityIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()

        Files.walk(projectPath)
            .filter { it.extension in listOf("java", "kt") }
            .filter { !it.toString().contains("/target/") }
            .filter { !it.toString().contains("/build/") }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Detect @RestController or @Controller
                if (content.contains("@RestController") || content.contains("@Controller")) {
                    // Find request mappings
                    val mappingPattern = Regex("""@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*\(\s*['"]([^'"]+)['"]""")
                    mappingPattern.findAll(content).forEach { match ->
                        val annotation = match.groupValues[1]
                        val path = match.groupValues[2]
                        val method = when (annotation) {
                            "GetMapping" -> "GET"
                            "PostMapping" -> "POST"
                            "PutMapping" -> "PUT"
                            "PatchMapping" -> "PATCH"
                            "DeleteMapping" -> "DELETE"
                            else -> "GET" // RequestMapping default
                        }
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
                                description = "Spring REST endpoint",
                                confidence = "CERTAIN",
                                evidenceRefs = listOf("$relativePath:$lineNum")
                            )
                        )
                    }
                }

                // Detect @Entity
                val entityPattern = Regex("""@Entity\s+(?:public\s+)?class\s+(\w+)""")
                entityPattern.findAll(content).forEach { match ->
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

                    insights.add(
                        FunctionalInsight(
                            category = "data-model",
                            title = "Entity: $className",
                            description = "JPA entity representing domain object",
                            confidence = "CERTAIN",
                            evidenceRefs = listOf("$relativePath:$lineNum")
                        )
                    )
                }

                // Detect @Service
                val servicePattern = Regex("""@Service\s+(?:public\s+)?class\s+(\w+)""")
                servicePattern.findAll(content).forEach { match ->
                    val className = match.groupValues[1]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    services.add(
                        ServiceIR(
                            id = className,
                            name = className,
                            fqn = "$relativePath.$className",
                            type = "class",
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
            services = services,
            endpoints = endpoints,
            entities = entities
        )

        val projectIR = ProjectIR(
            projectId = projectPath.fileName.toString(),
            name = projectPath.fileName.toString(),
            fingerprint = ProjectFingerprint(
                languages = mapOf("Java" to 1.0),
                frameworks = mapOf("Spring Boot" to 1.0),
                buildTools = mapOf("maven" to "pom.xml"),
                detectedFiles = listOf("pom.xml")
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
                name = "spring-security",
                pattern = Regex("""@EnableWebSecurity|@Secured|@PreAuthorize"""),
                category = "security",
                description = "Spring Security configuration detected"
            ),
            FunctionalRule(
                name = "jpa-repository",
                pattern = Regex("""interface\s+\w+\s+extends\s+JpaRepository"""),
                category = "data-access",
                description = "Spring Data JPA repository"
            )
        )
    }
}
