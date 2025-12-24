package io.docgen.llm.mock

import io.docgen.llm.api.*

/**
 * Mock LLM client pour fonctionnement sans LLM réel.
 * Retourne des réponses basiques basées sur le context fourni.
 */
class MockLLMClient : LLMClient {

    override suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T> {
        // Simulate processing
        val result = when (request.schema) {
            FeatureDescription::class.java -> createMockFeature(request.context)
            BusinessRules::class.java -> createMockBusinessRules(request.context)
            ArchitectureInsight::class.java -> createMockArchitectureInsight(request.context)
            UncertaintyReport::class.java -> createMockUncertaintyReport(request.context)
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        return LLMResponse(
            result = result as? T,
            confidence = 0.5,
            metadata = mapOf("mode" to "mock", "warning" to "LLM not configured, using fallback")
        )
    }

    override fun isAvailable(): Boolean = false // Mock is always "not really available"

    private fun createMockFeature(context: Map<String, Any>): FeatureDescription {
        val name = context["name"] as? String ?: "Unknown Feature"
        return FeatureDescription(
            name = name,
            category = "INCERTAIN",
            description = "Feature detected but LLM not configured for detailed analysis",
            userFacingCapability = "INCERTAIN - Requires LLM for inference",
            implementationSummary = "Code evidence found but detailed analysis unavailable",
            evidenceRefs = context["evidences"] as? List<String> ?: emptyList(),
            confidence = "INCERTAIN",
            uncertainties = listOf(
                "User-facing functionality unclear without LLM analysis",
                "Business context cannot be inferred without domain knowledge"
            )
        )
    }

    private fun createMockBusinessRules(context: Map<String, Any>): BusinessRules {
        return BusinessRules(
            rules = listOf(
                BusinessRule(
                    id = "mock-rule-1",
                    category = "validation",
                    description = "INCERTAIN - Business rules require LLM for inference",
                    trigger = "INCERTAIN",
                    action = "INCERTAIN",
                    evidenceRefs = emptyList(),
                    confidence = "INCERTAIN"
                )
            )
        )
    }

    private fun createMockArchitectureInsight(context: Map<String, Any>): ArchitectureInsight {
        val component = context["component"] as? String ?: "Unknown"
        return ArchitectureInsight(
            component = component,
            responsibility = "INCERTAIN - Requires LLM for semantic analysis",
            dependencies = context["dependencies"] as? List<String> ?: emptyList(),
            patterns = emptyList(),
            evidenceRefs = context["evidences"] as? List<String> ?: emptyList()
        )
    }

    private fun createMockUncertaintyReport(context: Map<String, Any>): UncertaintyReport {
        return UncertaintyReport(
            uncertainties = listOf(
                Uncertainty(
                    question = "What is the primary business purpose of this system?",
                    area = "functional",
                    impact = "HIGH - Cannot generate accurate functional documentation",
                    suggestedInvestigation = "Configure LLM client or manually review business requirements"
                ),
                Uncertainty(
                    question = "What are the key business rules and validation logic?",
                    area = "business-logic",
                    impact = "MEDIUM - Can document code structure but not business intent",
                    suggestedInvestigation = "Enable LLM for semantic analysis or consult domain experts"
                )
            )
        )
    }
}
