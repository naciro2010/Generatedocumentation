package io.docgen.parsing.detector

import io.docgen.core.model.ProjectFingerprint
import java.nio.file.Path

/**
 * Détecte les technologies d'un projet basé sur les fichiers manifests et extensions.
 */
interface TechDetector {
    fun detect(projectPath: Path): ProjectFingerprint
}

class CompositeTechDetector(
    private val detectors: List<LanguageDetector>
) : TechDetector {

    override fun detect(projectPath: Path): ProjectFingerprint {
        val languageScores = mutableMapOf<String, Double>()
        val frameworkScores = mutableMapOf<String, Double>()
        val buildTools = mutableMapOf<String, String>()
        val detectedFiles = mutableListOf<String>()

        detectors.forEach { detector ->
            val result = detector.detect(projectPath)
            result.languages.forEach { (lang, score) ->
                languageScores[lang] = (languageScores[lang] ?: 0.0) + score
            }
            result.frameworks.forEach { (fw, score) ->
                frameworkScores[fw] = (frameworkScores[fw] ?: 0.0) + score
            }
            buildTools.putAll(result.buildTools)
            detectedFiles.addAll(result.files)
        }

        return ProjectFingerprint(
            languages = languageScores.normalize(),
            frameworks = frameworkScores.normalize(),
            buildTools = buildTools,
            detectedFiles = detectedFiles.distinct()
        )
    }

    private fun Map<String, Double>.normalize(): Map<String, Double> {
        val sum = values.sum()
        return if (sum > 0) mapValues { it.value / sum } else this
    }
}

data class DetectionResult(
    val languages: Map<String, Double> = emptyMap(),
    val frameworks: Map<String, Double> = emptyMap(),
    val buildTools: Map<String, String> = emptyMap(),
    val files: List<String> = emptyList()
)

interface LanguageDetector {
    fun detect(projectPath: Path): DetectionResult
}
