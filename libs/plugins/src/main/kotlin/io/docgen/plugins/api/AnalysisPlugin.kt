package io.docgen.plugins.api

import io.docgen.core.model.ProjectFingerprint
import io.docgen.core.model.ProjectIR
import java.nio.file.Path

/**
 * Plugin API pour l'analyse de projets spécifiques.
 * Chaque plugin implémente l'analyse pour une technologie donnée.
 */
interface AnalysisPlugin {
    /**
     * Nom du plugin (ex: "node-express", "python-fastapi").
     */
    val name: String

    /**
     * Description courte.
     */
    val description: String

    /**
     * Détecte si ce plugin peut analyser le projet.
     * @return confidence score 0.0-1.0
     */
    fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double

    /**
     * Extrait les fragments IR du projet.
     */
    fun analyze(projectPath: Path): PluginAnalysisResult

    /**
     * Règles de détection fonctionnelle spécifiques à cette techno.
     */
    fun functionalRules(): List<FunctionalRule> = emptyList()
}

data class PluginAnalysisResult(
    val projectIR: ProjectIR,
    val functionalInsights: List<FunctionalInsight> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class FunctionalInsight(
    val category: String, // "feature", "business-rule", "integration", etc.
    val title: String,
    val description: String,
    val confidence: String, // CERTAIN, PROBABLE, HYPOTHESIS
    val evidenceRefs: List<String> = emptyList(),
    val uncertainties: List<String> = emptyList()
)

/**
 * Règle de détection fonctionnelle.
 */
data class FunctionalRule(
    val name: String,
    val pattern: Regex,
    val category: String,
    val description: String
)

/**
 * Registry des plugins.
 */
class PluginRegistry(
    private val plugins: List<AnalysisPlugin>
) {
    fun findBestPlugin(fingerprint: ProjectFingerprint, projectPath: Path): AnalysisPlugin? {
        return plugins
            .map { plugin -> plugin to plugin.detect(fingerprint, projectPath) }
            .filter { it.second > 0.5 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun findAllApplicablePlugins(fingerprint: ProjectFingerprint, projectPath: Path): List<AnalysisPlugin> {
        return plugins
            .filter { it.detect(fingerprint, projectPath) > 0.3 }
            .sortedByDescending { it.detect(fingerprint, projectPath) }
    }
}
