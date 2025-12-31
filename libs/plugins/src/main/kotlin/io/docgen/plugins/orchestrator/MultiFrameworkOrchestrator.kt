package io.docgen.plugins.orchestrator

import io.docgen.core.ir.ProjectIR
import io.docgen.core.ir.ModuleIR
import io.docgen.core.model.ProjectFingerprint
import io.docgen.plugins.api.AnalysisPlugin
import java.nio.file.Path

/**
 * Orchestrator that runs ALL applicable plugins and merges their results.
 * Instead of selecting a single "best" plugin, analyze with multiple plugins.
 */
class MultiFrameworkOrchestrator(private val plugins: List<AnalysisPlugin>) {

    data class OrchestrationResult(
        val projectIR: ProjectIR,
        val appliedPlugins: List<String>,
        val mergeReport: MergeReport
    )

    data class MergeReport(
        val pluginScores: Map<String, Double>,
        val conflictCount: Int,
        val mergedModules: Int,
        val warnings: List<String>
    )

    /**
     * Analyze project with ALL applicable plugins (score > 0.3)
     * and merge results intelligently.
     */
    fun analyzeWithMultiplePlugins(
        fingerprint: ProjectFingerprint,
        projectPath: Path
    ): OrchestrationResult {
        // 1. Score all plugins
        val pluginScores = plugins.map { plugin ->
            plugin.name to plugin.detect(fingerprint, projectPath)
        }.toMap()

        // 2. Select applicable plugins (score > 0.3)
        val applicablePlugins = plugins
            .filter { pluginScores[it.name]!! > 0.3 }
            .sortedByDescending { pluginScores[it.name]!! }

        if (applicablePlugins.isEmpty()) {
            throw IllegalStateException(
                "No applicable plugins found for project. Scores: $pluginScores"
            )
        }

        // 3. Run analysis on all applicable plugins
        val analysisResults = applicablePlugins.map { plugin ->
            plugin.name to plugin.analyze(projectPath)
        }

        // 4. Merge results intelligently
        val mergedIR = mergePluginResults(analysisResults.map { it.second })
        val warnings = detectConflicts(analysisResults)

        return OrchestrationResult(
            projectIR = mergedIR,
            appliedPlugins = applicablePlugins.map { it.name },
            mergeReport = MergeReport(
                pluginScores = pluginScores,
                conflictCount = warnings.size,
                mergedModules = mergedIR.modules.size,
                warnings = warnings
            )
        )
    }

    /**
     * Merge IR from multiple plugins intelligently:
     * - Union endpoints (avoid duplicates)
     * - Union services
     * - Keep all evidence
     */
    private fun mergePluginResults(results: List<ProjectIR>): ProjectIR {
        if (results.isEmpty()) throw IllegalStateException("No results to merge")
        if (results.size == 1) return results[0]

        val primaryIR = results[0]

        // Merge all modules from all plugins
        val mergedModules = mutableListOf<ModuleIR>()
        val seenModuleIds = mutableSetOf<String>()

        for (ir in results) {
            for (module in ir.modules) {
                // Avoid duplicate modules (same ID)
                if (!seenModuleIds.contains(module.id)) {
                    mergedModules.add(module)
                    seenModuleIds.add(module.id)
                } else {
                    // Module exists: merge endpoints and services
                    val existingModule = mergedModules.find { it.id == module.id }
                    if (existingModule != null) {
                        mergedModules.remove(existingModule)
                        mergedModules.add(mergeModules(existingModule, module))
                    }
                }
            }
        }

        // Return merged IR based on primary IR structure
        return primaryIR.copy(modules = mergedModules)
    }

    /**
     * Merge two ModuleIR instances:
     * - Union endpoints
     * - Union services
     * - Union entities
     */
    private fun mergeModules(module1: ModuleIR, module2: ModuleIR): ModuleIR {
        // Merge endpoints (avoid duplicates by path + method)
        val mergedEndpoints = (module1.endpoints + module2.endpoints)
            .distinctBy { "${it.method}:${it.path}" }

        // Merge services (avoid duplicates by FQN)
        val mergedServices = (module1.services + module2.services)
            .distinctBy { it.fqn }

        // Merge entities (avoid duplicates by FQN)
        val mergedEntities = (module1.entities + module2.entities)
            .distinctBy { it.fqn }

        return module1.copy(
            endpoints = mergedEndpoints,
            services = mergedServices,
            entities = mergedEntities
        )
    }

    /**
     * Detect conflicts between plugin results:
     * - Same endpoint with different handlers
     * - Inconsistent confidence levels
     */
    private fun detectConflicts(analysisResults: List<Pair<String, ProjectIR>>): List<String> {
        val warnings = mutableListOf<String>()

        // Check for conflicting endpoint definitions
        val endpointsByPath = mutableMapOf<String, MutableList<Pair<String, String>>>() // path -> (plugin, handler)

        for ((pluginName, ir) in analysisResults) {
            for (module in ir.modules) {
                for (endpoint in module.endpoints) {
                    val key = "${endpoint.method}:${endpoint.path}"
                    endpointsByPath.getOrPut(key) { mutableListOf() }
                        .add(pluginName to endpoint.handler)
                }
            }
        }

        // Report conflicting definitions
        for ((path, handlers) in endpointsByPath) {
            val uniqueHandlers = handlers.map { it.second }.distinct()
            if (uniqueHandlers.size > 1) {
                warnings.add(
                    "Conflict on $path: plugins detected different handlers: $uniqueHandlers"
                )
            }
        }

        return warnings
    }
}
