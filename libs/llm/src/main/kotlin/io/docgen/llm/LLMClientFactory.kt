package io.docgen.llm

import io.docgen.llm.anthropic.AnthropicClient
import io.docgen.llm.api.LLMClient
import io.docgen.llm.bedrock.BedrockClient
import io.docgen.llm.gemini.GeminiClient
import io.docgen.llm.mock.MockLLMClient
import io.docgen.llm.openai.OpenAIClient
import org.slf4j.LoggerFactory

/**
 * Factory to create the appropriate LLM client based on configuration.
 */
class LLMClientFactory {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createClient(config: LLMConfig): LLMClient {
        return when (config.provider) {
            LLMProvider.OPENAI -> {
                if (config.apiKey.isBlank()) {
                    logger.warn("OpenAI API key not configured, falling back to mock")
                    MockLLMClient()
                } else {
                    logger.info("Using OpenAI client with model: ${config.model}")
                    OpenAIClient(config.apiKey, config.model ?: "gpt-4-turbo-preview")
                }
            }
            LLMProvider.ANTHROPIC -> {
                if (config.apiKey.isBlank()) {
                    logger.warn("Anthropic API key not configured, falling back to mock")
                    MockLLMClient()
                } else {
                    logger.info("Using Anthropic client with model: ${config.model}")
                    AnthropicClient(config.apiKey, config.model ?: "claude-3-5-sonnet-20241022")
                }
            }
            LLMProvider.GEMINI -> {
                if (config.apiKey.isBlank()) {
                    logger.warn("Gemini API key not configured, falling back to mock")
                    MockLLMClient()
                } else {
                    logger.info("Using Google Gemini client with model: ${config.model}")
                    GeminiClient(config.apiKey, config.model ?: "gemini-pro")
                }
            }
            LLMProvider.BEDROCK -> {
                logger.info("Using AWS Bedrock client with model: ${config.model}")
                BedrockClient(
                    region = config.awsRegion ?: "us-east-1",
                    modelId = config.model ?: "anthropic.claude-3-sonnet-20240229-v1:0"
                )
            }
            LLMProvider.MOCK -> {
                logger.info("Using mock LLM client (no AI)")
                MockLLMClient()
            }
        }
    }
}

data class LLMConfig(
    val provider: LLMProvider = LLMProvider.MOCK,
    val apiKey: String = "",
    val model: String? = null,
    val enabled: Boolean = false,
    val awsRegion: String? = null
)

enum class LLMProvider {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    BEDROCK,
    MOCK
}
