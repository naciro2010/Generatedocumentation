package io.docgen.docs.generator

import io.docgen.core.model.ProjectIR
import io.docgen.plugins.api.FunctionalInsight
import java.nio.file.Path

/**
 * Génère la documentation complète d'un projet.
 */
interface DocumentationGenerator {
    fun generate(projectIR: ProjectIR, insights: List<FunctionalInsight>, outputPath: Path): GenerationResult
}

data class GenerationResult(
    val success: Boolean,
    val outputPath: Path,
    val generatedFiles: List<String>,
    val errors: List<String> = emptyList()
)

class StandardDocumentationGenerator(
    private val markdownRenderer: MarkdownRenderer,
    private val mermaidGenerator: MermaidGenerator
) : DocumentationGenerator {

    override fun generate(
        projectIR: ProjectIR,
        insights: List<FunctionalInsight>,
        outputPath: Path
    ): GenerationResult {
        val generatedFiles = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            // Create directory structure
            outputPath.toFile().mkdirs()
            val docsPath = outputPath.resolve("docs")
            docsPath.toFile().mkdirs()

            // Generate main documentation files
            generatedFiles.add(generateOverview(projectIR, docsPath))
            generatedFiles.add(generateArchitecture(projectIR, docsPath))
            generatedFiles.add(generateAPI(projectIR, docsPath))
            generatedFiles.add(generateDataModel(projectIR, docsPath))
            generatedFiles.add(generateFeatures(insights, docsPath))

            // Generate diagrams
            val diagramsPath = docsPath.resolve("diagrams")
            diagramsPath.toFile().mkdirs()
            generatedFiles.addAll(generateDiagrams(projectIR, diagramsPath))

            // Generate evidence mapping
            val evidencePath = docsPath.resolve("evidence")
            evidencePath.toFile().mkdirs()
            generatedFiles.add(generateEvidenceIndex(projectIR, insights, evidencePath))

            // Generate MkDocs config
            generatedFiles.add(generateMkDocsConfig(outputPath))

        } catch (e: Exception) {
            errors.add("Generation failed: ${e.message}")
        }

        return GenerationResult(
            success = errors.isEmpty(),
            outputPath = outputPath,
            generatedFiles = generatedFiles,
            errors = errors
        )
    }

    private fun generateOverview(projectIR: ProjectIR, outputPath: Path): String {
        val content = buildString {
            appendLine("# ${projectIR.name} - Overview")
            appendLine()
            appendLine("## Project Summary")
            appendLine()
            appendLine("- **Languages**: ${projectIR.fingerprint.languages.entries.joinToString { "${it.key} (${(it.value * 100).toInt()}%)" }}")
            appendLine("- **Frameworks**: ${projectIR.fingerprint.frameworks.keys.joinToString()}")
            appendLine("- **Build Tools**: ${projectIR.fingerprint.buildTools.values.joinToString()}")
            appendLine("- **Modules**: ${projectIR.modules.size}")
            appendLine()
            appendLine("## Modules")
            appendLine()
            projectIR.modules.forEach { module ->
                appendLine("### ${module.name}")
                appendLine("- Type: ${module.type}")
                appendLine("- Path: `${module.path}`")
                appendLine("- Services: ${module.services.size}")
                appendLine("- Endpoints: ${module.endpoints.size}")
                appendLine("- Entities: ${module.entities.size}")
                appendLine()
            }
        }

        val file = outputPath.resolve("overview.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateArchitecture(projectIR: ProjectIR, outputPath: Path): String {
        val content = buildString {
            appendLine("# Architecture")
            appendLine()
            appendLine("## System Context")
            appendLine()
            appendLine("```mermaid")
            appendLine(mermaidGenerator.generateC4Context(projectIR))
            appendLine("```")
            appendLine()
            appendLine("## Module Structure")
            appendLine()
            projectIR.modules.forEach { module ->
                appendLine("### ${module.name}")
                appendLine("Type: `${module.type}`")
                appendLine()
                if (module.services.isNotEmpty()) {
                    appendLine("**Services:**")
                    module.services.forEach { service ->
                        appendLine("- `${service.name}` (${service.type})")
                    }
                    appendLine()
                }
            }
        }

        val file = outputPath.resolve("architecture.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateAPI(projectIR: ProjectIR, outputPath: Path): String {
        val content = buildString {
            appendLine("# API Reference")
            appendLine()
            val allEndpoints = projectIR.modules.flatMap { it.endpoints }

            if (allEndpoints.isEmpty()) {
                appendLine("_No API endpoints detected._")
            } else {
                appendLine("## Endpoints")
                appendLine()
                allEndpoints.sortedBy { it.path }.forEach { endpoint ->
                    appendLine("### `${endpoint.method} ${endpoint.path}`")
                    appendLine()
                    appendLine("**Handler**: `${endpoint.handler}`")
                    appendLine()
                    if (endpoint.evidences.isNotEmpty()) {
                        appendLine("**Evidence**:")
                        endpoint.evidences.forEach { evidence ->
                            appendLine("- ${evidence.toReference()}")
                        }
                        appendLine()
                    }
                }
            }
        }

        val file = outputPath.resolve("api.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateDataModel(projectIR: ProjectIR, outputPath: Path): String {
        val content = buildString {
            appendLine("# Data Model")
            appendLine()
            val allEntities = projectIR.modules.flatMap { it.entities }

            if (allEntities.isEmpty()) {
                appendLine("_No entities detected._")
            } else {
                appendLine("## Entities")
                appendLine()
                appendLine("```mermaid")
                appendLine(mermaidGenerator.generateERDiagram(allEntities))
                appendLine("```")
                appendLine()

                allEntities.forEach { entity ->
                    appendLine("### ${entity.name}")
                    appendLine()
                    if (entity.table != null) {
                        appendLine("**Table**: `${entity.table}`")
                        appendLine()
                    }
                    if (entity.fields.isNotEmpty()) {
                        appendLine("**Fields**:")
                        appendLine()
                        appendLine("| Field | Type | Nullable |")
                        appendLine("|-------|------|----------|")
                        entity.fields.forEach { field ->
                            appendLine("| ${field.name} | ${field.type} | ${if (field.nullable) "Yes" else "No"} |")
                        }
                        appendLine()
                    }
                    if (entity.evidences.isNotEmpty()) {
                        appendLine("**Evidence**: ${entity.evidences.first().toReference()}")
                        appendLine()
                    }
                }
            }
        }

        val file = outputPath.resolve("data-model.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateFeatures(insights: List<FunctionalInsight>, outputPath: Path): String {
        val content = buildString {
            appendLine("# Features & Functional Insights")
            appendLine()

            val grouped = insights.groupBy { it.category }

            grouped.forEach { (category, categoryInsights) ->
                appendLine("## ${category.replaceFirstChar { it.uppercase() }}")
                appendLine()
                categoryInsights.forEach { insight ->
                    appendLine("### ${insight.title}")
                    appendLine()
                    appendLine(insight.description)
                    appendLine()
                    appendLine("**Confidence**: ${insight.confidence}")
                    appendLine()
                    if (insight.evidenceRefs.isNotEmpty()) {
                        appendLine("**Evidence**:")
                        insight.evidenceRefs.forEach { ref ->
                            appendLine("- `$ref`")
                        }
                        appendLine()
                    }
                    if (insight.uncertainties.isNotEmpty()) {
                        appendLine("**Uncertainties**:")
                        insight.uncertainties.forEach { u ->
                            appendLine("- $u")
                        }
                        appendLine()
                    }
                }
            }
        }

        val file = outputPath.resolve("features.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateDiagrams(projectIR: ProjectIR, outputPath: Path): List<String> {
        // Diagrams are embedded in markdown files as mermaid code blocks
        return emptyList()
    }

    private fun generateEvidenceIndex(
        projectIR: ProjectIR,
        insights: List<FunctionalInsight>,
        outputPath: Path
    ): String {
        val content = buildString {
            appendLine("# Evidence Index")
            appendLine()
            appendLine("This file maps all assertions to their source code evidence.")
            appendLine()

            // Collect all evidences
            val evidences = mutableListOf<Pair<String, String>>()

            projectIR.modules.forEach { module ->
                module.endpoints.forEach { endpoint ->
                    endpoint.evidences.forEach { evidence ->
                        evidences.add("Endpoint ${endpoint.method} ${endpoint.path}" to evidence.toReference())
                    }
                }
                module.entities.forEach { entity ->
                    entity.evidences.forEach { evidence ->
                        evidences.add("Entity ${entity.name}" to evidence.toReference())
                    }
                }
            }

            insights.forEach { insight ->
                insight.evidenceRefs.forEach { ref ->
                    evidences.add(insight.title to ref)
                }
            }

            evidences.sortedBy { it.second }.forEach { (assertion, ref) ->
                appendLine("- **$assertion**: `$ref`")
            }
        }

        val file = outputPath.resolve("evidence-index.md")
        file.toFile().writeText(content)
        return file.toString()
    }

    private fun generateMkDocsConfig(outputPath: Path): String {
        val content = """
site_name: Project Documentation
theme:
  name: material
  palette:
    primary: indigo
nav:
  - Overview: overview.md
  - Architecture: architecture.md
  - API Reference: api.md
  - Data Model: data-model.md
  - Features: features.md
  - Evidence Index: evidence/evidence-index.md
        """.trimIndent()

        val file = outputPath.resolve("mkdocs.yml")
        file.toFile().writeText(content)
        return file.toString()
    }
}

interface MarkdownRenderer {
    fun render(template: String, context: Map<String, Any>): String
}

interface MermaidGenerator {
    fun generateC4Context(projectIR: ProjectIR): String
    fun generateERDiagram(entities: List<io.docgen.core.model.EntityIR>): String
}
