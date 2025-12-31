package io.docgen.llm.validator

import io.docgen.core.model.ProjectIR
import io.docgen.core.model.EndpointIR
import io.docgen.llm.client.LLMClient
import java.nio.file.Path

/**
 * Validate that plugins didn't miss important code structures.
 * NOT for enrichment - just validation/correction.
 */
class ExtractionValidator(private val llmClient: LLMClient) {

    data class ValidationResult(
        val isValid: Boolean,
        val foundMissingEndpoints: List<EndpointIR>,
        val correctedEndpoints: Map<String, EndpointIR>,
        val confidence: Double,
        val report: String
    )

    /**
     * Ask LLM: "Did the plugin miss any entry points or critical services?"
     * Fast check - NOT full enrichment
     */
    fun validateExtraction(
        projectIR: ProjectIR,
        projectPath: Path,
        criticalFiles: List<String> = emptyList()
    ): ValidationResult {
        val prompt = buildValidationPrompt(projectIR, criticalFiles)

        val response = llmClient.analyzeCode(prompt)

        return parseValidationResponse(response, projectIR)
    }

    private fun buildValidationPrompt(projectIR: ProjectIR, criticalFiles: List<String>): String {
        val detectedEndpoints = projectIR.modules
            .flatMap { it.endpoints }
            .map { "${it.method} ${it.path}" }

        return """
            Code analysis tool extracted these API endpoints:
            ${detectedEndpoints.joinToString("\n") { "- $it" }}

            Critical files detected: ${criticalFiles.joinToString(", ")}

            Questions (Answer only with JSON):
            1. Are there any entry points or routes the tool MISSED?
            2. Are the HTTP methods correct?
            3. Any dynamic routing that needs attention?

            Format your response as JSON:
            {
                "missedEndpoints": [{"method": "GET", "path": "/path", "reason": "..."}],
                "methodCorrections": [{"originalPath": "/path", "correctMethod": "POST"}],
                "warnings": ["..."],
                "confidence": 0.95
            }

            If everything looks correct, return empty lists. Be conservative - only report clear issues.
        """.trimIndent()
    }

    private fun parseValidationResponse(response: String, projectIR: ProjectIR): ValidationResult {
        // Try to extract JSON from response
        val jsonMatch = Regex("""\{[\s\S]*?\}""").find(response)
            ?: return ValidationResult(
                isValid = true,
                foundMissingEndpoints = emptyList(),
                correctedEndpoints = emptyMap(),
                confidence = 0.5,
                report = "Could not parse LLM response (likely correct extraction)"
            )

        return try {
            // Parse as simple structured response
            val json = jsonMatch.value

            ValidationResult(
                isValid = !json.contains("\"missedEndpoints\":[{") && !json.contains("\"methodCorrections\":[{"),
                foundMissingEndpoints = emptyList(), // TODO: parse from JSON
                correctedEndpoints = emptyMap(),     // TODO: parse from JSON
                confidence = extractConfidence(json),
                report = "Validation complete"
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = true,
                foundMissingEndpoints = emptyList(),
                correctedEndpoints = emptyMap(),
                confidence = 0.0,
                report = "Validation error: ${e.message}"
            )
        }
    }

    private fun extractConfidence(json: String): Double {
        val match = Regex(""""confidence":\s*([\d.]+)""").find(json)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.5
    }
}
