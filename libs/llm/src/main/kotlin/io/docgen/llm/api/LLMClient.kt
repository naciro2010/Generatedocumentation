package io.docgen.llm.api

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Interface pour les clients LLM.
 * Design: structured output only, no free-form text generation.
 */
interface LLMClient {
    /**
     * Génère du contenu structuré à partir d'un prompt et d'un schema.
     */
    suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T>

    /**
     * Check if LLM is available/configured.
     */
    fun isAvailable(): Boolean
}

data class LLMRequest<T>(
    val systemPrompt: String,
    val userPrompt: String,
    val schema: Class<T>,
    val context: Map<String, Any> = emptyMap(),
    val temperature: Double = 0.0 // Deterministic by default
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LLMResponse<T>(
    val result: T?,
    val confidence: Double? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Schema examples for structured outputs.
 */
data class FeatureDescription(
    val name: String,
    val category: String,
    val description: String,
    val userFacingCapability: String,
    val implementationSummary: String,
    val evidenceRefs: List<String>,
    val confidence: String,
    val uncertainties: List<String> = emptyList()
)

data class BusinessRules(
    val rules: List<BusinessRule>
)

data class BusinessRule(
    val id: String,
    val category: String,
    val description: String,
    val trigger: String,
    val action: String,
    val evidenceRefs: List<String>,
    val confidence: String
)

data class ArchitectureInsight(
    val component: String,
    val responsibility: String,
    val dependencies: List<String>,
    val patterns: List<String>,
    val evidenceRefs: List<String>
)

data class UncertaintyReport(
    val uncertainties: List<Uncertainty>
)

data class Uncertainty(
    val question: String,
    val area: String,
    val impact: String,
    val suggestedInvestigation: String
)
