package io.docgen.llm.openai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.docgen.llm.api.LLMClient
import io.docgen.llm.api.LLMRequest
import io.docgen.llm.api.LLMResponse
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * OpenAI GPT implementation.
 */
class OpenAIClient(
    private val apiKey: String,
    private val model: String = "gpt-4-turbo-preview"
) : LLMClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
    private val client = OpenAI(apiKey)

    override suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T> {
        return try {
            val chatRequest = ChatCompletionRequest(
                model = ModelId(model),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = request.systemPrompt
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = buildUserPrompt(request)
                    )
                ),
                temperature = request.temperature
            )

            val completion = client.chatCompletion(chatRequest)
            val responseText = completion.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("No response from OpenAI")

            // Parse JSON response to target type
            val result = parseResponse(responseText, request.schema)

            LLMResponse(
                result = result,
                confidence = 0.85,
                metadata = mapOf(
                    "model" to model,
                    "tokens" to (completion.usage?.totalTokens ?: 0)
                )
            )
        } catch (e: Exception) {
            logger.error("OpenAI request failed", e)
            LLMResponse(
                result = null,
                error = "OpenAI error: ${e.message}"
            )
        }
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private fun <T> buildUserPrompt(request: LLMRequest<T>): String {
        val schemaJson = objectMapper.writeValueAsString(request.context)
        return """
            ${request.userPrompt}

            Context:
            $schemaJson

            Please respond with valid JSON matching this schema: ${request.schema.simpleName}
        """.trimIndent()
    }

    private fun <T> parseResponse(responseText: String, schema: Class<T>): T? {
        return try {
            // Extract JSON from markdown code blocks if present
            val jsonText = responseText
                .substringAfter("```json", responseText)
                .substringAfter("```", responseText)
                .substringBefore("```", responseText)
                .trim()

            objectMapper.readValue(jsonText, schema)
        } catch (e: Exception) {
            logger.error("Failed to parse OpenAI response", e)
            null
        }
    }
}
