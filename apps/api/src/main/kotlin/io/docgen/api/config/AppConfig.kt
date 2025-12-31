package io.docgen.api.config

import io.docgen.core.storage.LocalFileStore
import io.docgen.core.storage.ProjectStore
import io.docgen.llm.validator.ExtractionValidator
import io.docgen.plugins.api.AnalysisPlugin
import io.docgen.plugins.impl.JavaSpringPlugin
import io.docgen.plugins.impl.JavaLegacyPlugin
import io.docgen.plugins.impl.PHPLegacyPlugin
import io.docgen.plugins.impl.NodeExpressPlugin
import io.docgen.plugins.impl.PythonFastAPIPlugin
import io.docgen.plugins.orchestrator.MultiFrameworkOrchestrator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths

/**
 * Simplified configuration without Spring Data JPA, PostgreSQL, Flyway
 * Everything is file-based JSON storage
 */
@Configuration
class SimpleAppConfig {

    @Bean
    fun projectStore(
        @Value("\${docgen.storage-path:./storage}") storagePath: String
    ): ProjectStore {
        return LocalFileStore(Paths.get(storagePath))
    }

    @Bean
    fun analysisPlugins(): List<AnalysisPlugin> {
        return listOf(
            JavaSpringPlugin(),
            JavaLegacyPlugin(),
            PHPLegacyPlugin(),
            NodeExpressPlugin(),
            PythonFastAPIPlugin()
            // Add more plugins as needed
        )
    }

    @Bean
    fun multiFrameworkOrchestrator(plugins: List<AnalysisPlugin>): MultiFrameworkOrchestrator {
        return MultiFrameworkOrchestrator(plugins)
    }

    @Bean
    fun extractionValidator(): ExtractionValidator {
        // Note: LLMClient would need to be injected here in real implementation
        return ExtractionValidator(object : io.docgen.llm.client.LLMClient {
            override fun analyzeCode(prompt: String): String {
                // Mock implementation - replace with real LLM client
                return """{"confidence": 0.95, "missedEndpoints": []}"""
            }
        })
    }
}
