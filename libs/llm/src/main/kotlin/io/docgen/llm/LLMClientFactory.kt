package io.docgen.llm

import io.docgen.llm.anthropic.AnthropicClient
import io.docgen.llm.api.LLMClient
import io.docgen.llm.mock.MockLLMClient
import io.docgen.llm.openai.OpenAIClient
import org.slf4j.LoggerFactory

/**
 * Factory pour créer le bon client LLM selon la configuration.
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
            LLMProvider.MOCK -> {
                logger.info("Using mock LLM client")
                MockLLMClient()
            }
        }
    }
}

data class LLMConfig(
    val provider: LLMProvider = LLMProvider.MOCK,
    val apiKey: String = "",
    val model: String? = null,
    val enabled: Boolean = false
)

enum class LLMProvider {
    OPENAI,
    ANTHROPIC,
    MOCK
}
