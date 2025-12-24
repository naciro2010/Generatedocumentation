package io.docgen.llm.bedrock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.docgen.llm.api.LLMClient
import io.docgen.llm.api.LLMRequest
import io.docgen.llm.api.LLMResponse
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import kotlinx.coroutines.future.await

/**
 * AWS Bedrock client.
 * Supports Claude models via AWS Bedrock.
 */
class BedrockClient(
    private val region: String = "us-east-1",
    private val modelId: String = "anthropic.claude-3-sonnet-20240229-v1:0"
) : LLMClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    private val bedrockClient = BedrockRuntimeAsyncClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build()

    override suspend fun <T> generateStructured(request: LLMRequest<T>): LLMResponse<T> {
        return try {
            val prompt = buildPrompt(request)

            // Bedrock request format for Claude
            val requestBody = mapOf(
                "anthropic_version" to "bedrock-2023-05-31",
                "max_tokens" to 4096,
                "temperature" to request.temperature,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                ),
                "system" to request.systemPrompt
            )

            val requestJson = objectMapper.writeValueAsString(requestBody)

            val invokeRequest = InvokeModelRequest.builder()
                .modelId(modelId)
                .body(SdkBytes.fromUtf8String(requestJson))
                .build()

            val response = bedrockClient.invokeModel(invokeRequest).await()
            val responseBody = response.body().asUtf8String()

            val responseMap = objectMapper.readValue(responseBody, Map::class.java)
            val content = extractContent(responseMap)
            val result = parseResponse(content, request.schema)

            LLMResponse(
                result = result,
                confidence = 0.9,
                metadata = mapOf(
                    "model" to modelId,
                    "provider" to "bedrock",
                    "region" to region
                )
            )
        } catch (e: Exception) {
            logger.error("Bedrock request failed", e)
            LLMResponse(
                result = null,
                error = "Bedrock error: ${e.message}"
            )
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            // Check if AWS credentials are configured
            DefaultCredentialsProvider.create().resolveCredentials()
            true
        } catch (e: Exception) {
            logger.warn("AWS credentials not configured: ${e.message}")
            false
        }
    }

    private fun <T> buildPrompt(request: LLMRequest<T>): String {
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
    private fun extractContent(response: Map<*, *>): String {
        val content = response["content"] as? List<Map<*, *>>
            ?: throw IllegalStateException("No content in Bedrock response")

        val firstContent = content.firstOrNull()
            ?: throw IllegalStateException("Empty content list")

        return firstContent["text"] as? String
            ?: throw IllegalStateException("No text in content")
    }

    private fun <T> parseResponse(responseText: String, schema: Class<T>): T? {
        return try {
            val jsonText = responseText
                .substringAfter("```json", responseText)
                .substringAfter("```", responseText)
                .substringBefore("```", responseText)
                .trim()

            objectMapper.readValue(jsonText, schema)
        } catch (e: Exception) {
            logger.error("Failed to parse Bedrock response", e)
            null
        }
    }
}
