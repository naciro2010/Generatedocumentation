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
 * Plugin pour projets Ruby/Rails.
 */
class RubyRailsPlugin : AnalysisPlugin {
    override val name = "ruby-rails"
    override val description = "Analyze Ruby projects using Rails framework"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Ruby")) {
            score += 0.4
        }

        // Check Gemfile for Rails
        val gemfile = projectPath.resolve("Gemfile")
        if (Files.exists(gemfile)) {
            val content = gemfile.readText()
            if (content.contains("gem 'rails'") || content.contains("gem \"rails\"")) {
                score += 0.6
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val entities = mutableListOf<EntityIR>()
        val insights = mutableListOf<FunctionalInsight>()

        // Find controllers in app/controllers
        val controllersPath = projectPath.resolve("app/controllers")
        if (Files.exists(controllersPath)) {
            Files.walk(controllersPath)
                .filter { it.extension == "rb" }
                .forEach { file ->
                    val content = file.readText()
                    val relativePath = projectPath.relativize(file).toString()

                    // Detect controller actions
                    val actionPattern = Regex("""def\s+(index|show|new|create|edit|update|destroy|\w+)""")
                    actionPattern.findAll(content).forEach { match ->
                        val action = match.groupValues[1]
                        val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                        // Infer HTTP method and path
                        val (method, path) = inferRailsRoute(action, relativePath)

                        endpoints.add(
                            EndpointIR(
                                id = "$relativePath:$lineNum",
                                method = method,
                                path = path,
                                handler = "$relativePath:$lineNum",
                                evidences = listOf(
                                    Evidence(
                                        filePath = relativePath,
                                        startLine = lineNum,
                                        endLine = lineNum,
                                        symbol = "action:$action"
                                    )
                                )
                            )
                        )

                        insights.add(
                            FunctionalInsight(
                                category = "feature",
                                title = "Rails Action: $action",
                                description = "Rails controller action",
                                confidence = "PROBABLE",
                                evidenceRefs = listOf("$relativePath:$lineNum")
                            )
                        )
                    }
                }
        }

        // Find models in app/models
        val modelsPath = projectPath.resolve("app/models")
        if (Files.exists(modelsPath)) {
            Files.walk(modelsPath)
                .filter { it.extension == "rb" }
                .forEach { file ->
                    val content = file.readText()
                    val relativePath = projectPath.relativize(file).toString()

                    val modelPattern = Regex("""class\s+(\w+)\s+<\s+ApplicationRecord""")
                    modelPattern.findAll(content).forEach { match ->
                        val className = match.groupValues[1]
                        val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                        entities.add(
                            EntityIR(
                                id = className,
                                name = className,
                                fqn = "$relativePath.$className",
                                table = className.toLowerCase() + "s",
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
                languages = mapOf("Ruby" to 1.0),
                frameworks = mapOf("Rails" to 1.0),
                buildTools = mapOf("bundler" to "Gemfile"),
                detectedFiles = listOf("Gemfile")
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
                name = "activerecord-validation",
                pattern = Regex("""validates"""),
                category = "validation",
                description = "ActiveRecord validation"
            ),
            FunctionalRule(
                name = "devise-authentication",
                pattern = Regex("""devise"""),
                category = "security",
                description = "Devise authentication"
            )
        )
    }

    private fun inferRailsRoute(action: String, controllerPath: String): Pair<HttpMethod, String> {
        val controllerName = controllerPath
            .substringAfterLast("/")
            .substringBefore("_controller.rb")

        return when (action) {
            "index" -> HttpMethod.GET to "/$controllerName"
            "show" -> HttpMethod.GET to "/$controllerName/:id"
            "new" -> HttpMethod.GET to "/$controllerName/new"
            "create" -> HttpMethod.POST to "/$controllerName"
            "edit" -> HttpMethod.GET to "/$controllerName/:id/edit"
            "update" -> HttpMethod.PUT to "/$controllerName/:id"
            "destroy" -> HttpMethod.DELETE to "/$controllerName/:id"
            else -> HttpMethod.GET to "/$controllerName/$action"
        }
    }
}
