package io.docgen.llm.anthropic

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
 * Anthropic Claude implementation.
 */
class AnthropicClient(
    private val apiKey: String,
    private val model: String = "claude-3-5-sonnet-20241022"
) : LLMClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com/v1")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .build()

    override suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T> {
        return try {
            val requestBody = mapOf(
                "model" to model,
                "max_tokens" to 4096,
                "temperature" to request.temperature,
                "system" to request.systemPrompt,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to buildUserPrompt(request)
                    )
                )
            )

            val response = webClient.post()
                .uri("/messages")
                .bodyValue(requestBody)
                .retrieve()
                .awaitBody<Map<String, Any>>()

            val content = extractContent(response)
            val result = parseResponse(content, request.schema)

            LLMResponse(
                result = result,
                confidence = 0.9,
                metadata = mapOf(
                    "model" to model,
                    "stop_reason" to (response["stop_reason"] ?: "unknown")
                )
            )
        } catch (e: Exception) {
            logger.error("Anthropic request failed", e)
            LLMResponse(
                result = null,
                error = "Anthropic error: ${e.message}"
            )
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private fun <T> buildUserPrompt(request: LLMRequest<T>): String {
        val contextJson = objectMapper.writeValueAsString(request.context)
        return """
            ${request.userPrompt}

            Context:
            $contextJson

            Respond with valid JSON matching this schema: ${request.schema.simpleName}
            Do not include markdown formatting, just raw JSON.
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractContent(response: Map<String, Any>): String {
        val content = response["content"] as? List<Map<String, Any>>
            ?: throw IllegalStateException("No content in response")

        return content.firstOrNull()?.get("text") as? String
            ?: throw IllegalStateException("No text in content")
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
            logger.error("Failed to parse Anthropic response", e)
            null
        }
    }
}
