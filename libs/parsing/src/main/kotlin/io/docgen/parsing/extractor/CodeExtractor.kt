package io.docgen.parsing.extractor

import io.docgen.core.domain.Evidence
import io.docgen.core.model.SymbolType
import java.nio.file.Path

/**
 * Extrait les symboles de code d'un fichier.
 */
interface CodeExtractor {
    fun canHandle(filePath: Path): Boolean
    fun extractSymbols(filePath: Path, content: String): List<ExtractedSymbol>
}

data class ExtractedSymbol(
    val name: String,
    val fqn: String,
    val type: SymbolType,
    val startLine: Int,
    val endLine: Int,
    val signature: String? = null,
    val docstring: String? = null,
    val imports: List<String> = emptyList(),
    val calls: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    fun toEvidence(filePath: String): Evidence =
        Evidence(
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            symbol = name,
            snippet = signature
        )
}

/**
 * Extracteur simple basé sur regex (fallback quand Tree-sitter n'est pas dispo).
 */
class RegexCodeExtractor : CodeExtractor {

    private val patterns = mapOf(
        "kt" to KotlinPatterns,
        "java" to JavaPatterns,
        "py" to PythonPatterns,
        "js" to JavaScriptPatterns,
        "ts" to TypeScriptPatterns
    )

    override fun canHandle(filePath: Path): Boolean {
        val ext = filePath.toFile().extension
        return ext in patterns.keys
    }

    override fun extractSymbols(filePath: Path, content: String): List<ExtractedSymbol> {
        val ext = filePath.toFile().extension
        val patternSet = patterns[ext] ?: return emptyList()

        val symbols = mutableListOf<ExtractedSymbol>()
        val lines = content.lines()

        // Extract classes
        patternSet.classPattern?.let { pattern ->
            pattern.findAll(content).forEach { match ->
                val name = match.groupValues.getOrNull(1) ?: return@forEach
                val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1
                symbols.add(
                    ExtractedSymbol(
                        name = name,
                        fqn = name, // Simplified, should extract package
                        type = SymbolType.CLASS,
                        startLine = lineNum,
                        endLine = lineNum,
                        signature = match.value
                    )
                )
            }
        }

        // Extract functions
        patternSet.functionPattern?.let { pattern ->
            pattern.findAll(content).forEach { match ->
                val name = match.groupValues.getOrNull(1) ?: return@forEach
                val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1
                symbols.add(
                    ExtractedSymbol(
                        name = name,
                        fqn = name,
                        type = SymbolType.FUNCTION,
                        startLine = lineNum,
                        endLine = lineNum,
                        signature = match.value
                    )
                )
            }
        }

        return symbols
    }
}

// Pattern sets for different languages
object KotlinPatterns {
    val classPattern = Regex("""(?:class|interface|object|data class|sealed class)\s+(\w+)""")
    val functionPattern = Regex("""fun\s+(\w+)\s*\([^)]*\)""")
}

object JavaPatterns {
    val classPattern = Regex("""(?:public\s+)?(?:class|interface|enum)\s+(\w+)""")
    val functionPattern = Regex("""(?:public|private|protected)?\s+[\w<>]+\s+(\w+)\s*\([^)]*\)""")
}

object PythonPatterns {
    val classPattern = Regex("""class\s+(\w+)""")
    val functionPattern = Regex("""def\s+(\w+)\s*\([^)]*\)""")
}

object JavaScriptPatterns {
    val classPattern = Regex("""class\s+(\w+)""")
    val functionPattern = Regex("""(?:function\s+(\w+)|(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s+)?(?:function|\([^)]*\)\s*=>))""")
}

object TypeScriptPatterns {
    val classPattern = Regex("""(?:export\s+)?class\s+(\w+)""")
    val functionPattern = Regex("""(?:export\s+)?(?:async\s+)?function\s+(\w+)|(?:const|let)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>""")
}
