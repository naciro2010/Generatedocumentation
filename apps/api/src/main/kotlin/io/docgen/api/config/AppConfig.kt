package io.docgen.api.config

import io.docgen.docs.generator.DocumentationGenerator
import io.docgen.docs.generator.MarkdownRenderer
import io.docgen.docs.generator.MermaidGenerator
import io.docgen.docs.generator.StandardDocumentationGenerator
import io.docgen.docs.mermaid.MermaidGeneratorImpl
import io.docgen.llm.api.LLMClient
import io.docgen.llm.mock.MockLLMClient
import io.docgen.parsing.detector.CompositeTechDetector
import io.docgen.parsing.detector.FileBasedDetector
import io.docgen.parsing.detector.TechDetector
import io.docgen.parsing.extractor.CodeExtractor
import io.docgen.parsing.extractor.RegexCodeExtractor
import io.docgen.plugins.api.AnalysisPlugin
import io.docgen.plugins.api.PluginRegistry
import io.docgen.plugins.impl.JavaSpringPlugin
import io.docgen.plugins.impl.NodeExpressPlugin
import io.docgen.plugins.impl.PythonFastAPIPlugin
import io.docgen.plugins.impl.legacy.JavaLegacyPlugin
import io.docgen.plugins.impl.legacy.PHPLegacyPlugin
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppConfig {

    @Bean
    fun techDetector(): TechDetector {
        return CompositeTechDetector(
            detectors = listOf(
                FileBasedDetector()
            )
        )
    }

    @Bean
    fun codeExtractor(): CodeExtractor {
        return RegexCodeExtractor()
    }

    @Bean
    fun analysisPlugins(): List<AnalysisPlugin> {
        return listOf(
            // Modern frameworks
            NodeExpressPlugin(),
            PythonFastAPIPlugin(),
            JavaSpringPlugin(),
            io.docgen.plugins.impl.RubyRailsPlugin(),
            io.docgen.plugins.impl.GoPlugin(),
            io.docgen.plugins.impl.RustPlugin(),
            // Legacy frameworks (critical for old codebases)
            JavaLegacyPlugin(),
            PHPLegacyPlugin()
        )
    }

    @Bean
    fun pluginRegistry(plugins: List<AnalysisPlugin>): PluginRegistry {
        return PluginRegistry(plugins)
    }

    @Bean
    fun llmClient(): LLMClient {
        // TODO: Allow configuration of real LLM client
        return MockLLMClient()
    }

    @Bean
    fun mermaidGenerator(): MermaidGenerator {
        return MermaidGeneratorImpl()
    }

    @Bean
    fun markdownRenderer(): MarkdownRenderer {
        return SimpleMarkdownRenderer()
    }

    @Bean
    fun documentationGenerator(
        markdownRenderer: MarkdownRenderer,
        mermaidGenerator: MermaidGenerator
    ): DocumentationGenerator {
        return StandardDocumentationGenerator(markdownRenderer, mermaidGenerator)
    }
}

class SimpleMarkdownRenderer : MarkdownRenderer {
    override fun render(template: String, context: Map<String, Any>): String {
        var result = template
        context.forEach { (key, value) ->
            result = result.replace("\${$key}", value.toString())
        }
        return result
    }
}

@ConfigurationProperties(prefix = "docgen")
data class DocGenProperties(
    var storagePath: String = "./storage",
    var maxProjectSize: Long = 1_000_000_000, // 1GB
    var llmEnabled: Boolean = false
)
