package io.docgen.llm.gemini

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.docgen.llm.api.LLMClient
import io.docgen.llm.api.LLMRequest
import io.docgen.llm.api.LLMResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Google Gemini AI client.
 * Uses the Gemini API for structured output generation.
 */
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-pro"
) : LLMClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T> {
        return try {
            val prompt = buildPrompt(request)

            val requestBody = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to prompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "temperature" to request.temperature,
                    "maxOutputTokens" to 4096
                )
            )

            val response = webClient.post()
                .uri("/models/${model}:generateContent?key=${apiKey}")
                .bodyValue(requestBody)
                .retrieve()
                .awaitBody<Map<String, Any>>()

            val content = extractContent(response)
            val result = parseResponse(content, request.schema)

            LLMResponse(
                result = result,
                confidence = 0.85,
                metadata = mapOf(
                    "model" to model,
                    "provider" to "gemini"
                )
            )
        } catch (e: Exception) {
            logger.error("Gemini request failed", e)
            LLMResponse(
                result = null,
                error = "Gemini error: ${e.message}"
            )
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private fun <T> buildPrompt(request: LLMRequest<T>): String {
        val contextJson = objectMapper.writeValueAsString(request.context)
        return """
            ${request.systemPrompt}

            ${request.userPrompt}

            Context:
            $contextJson

            IMPORTANT: Respond ONLY with valid JSON matching this schema: ${request.schema.simpleName}
            Do not include any markdown formatting or explanations. Just pure JSON.
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractContent(response: Map<String, Any>): String {
        val candidates = response["candidates"] as? List<Map<String, Any>>
            ?: throw IllegalStateException("No candidates in Gemini response")

        val firstCandidate = candidates.firstOrNull()
            ?: throw IllegalStateException("Empty candidates list")

        val content = firstCandidate["content"] as? Map<String, Any>
            ?: throw IllegalStateException("No content in candidate")

        val parts = content["parts"] as? List<Map<String, Any>>
            ?: throw IllegalStateException("No parts in content")

        val firstPart = parts.firstOrNull()
            ?: throw IllegalStateException("Empty parts list")

        return firstPart["text"] as? String
            ?: throw IllegalStateException("No text in part")
    }

    private fun <T> parseResponse(responseText: String, schema: Class<T>): T? {
        return try {
            // Clean up response (remove markdown if present)
            val jsonText = responseText
                .substringAfter("```json", responseText)
                .substringAfter("```", responseText)
                .substringBefore("```", responseText)
                .trim()

            objectMapper.readValue(jsonText, schema)
        } catch (e: Exception) {
            logger.error("Failed to parse Gemini response: $responseText", e)
            null
        }
    }
}
