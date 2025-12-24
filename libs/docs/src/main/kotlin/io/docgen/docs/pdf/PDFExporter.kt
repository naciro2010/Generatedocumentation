package io.docgen.docs.pdf

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Exporte la documentation en PDF.
 */
class PDFExporter {

    private val markdownParser = Parser.builder().build()
    private val htmlRenderer = HtmlRenderer.builder().build()

    fun exportToPDF(docsPath: Path, outputPath: Path) {
        val htmlContent = buildHTMLDocument(docsPath)

        Files.newOutputStream(outputPath).use { outputStream ->
            renderPDF(htmlContent, outputStream)
        }
    }

    private fun buildHTMLDocument(docsPath: Path): String {
        val sections = mutableListOf<String>()

        // Read all markdown files
        listOf("overview.md", "architecture.md", "api.md", "data-model.md", "features.md")
            .forEach { fileName ->
                val file = docsPath.resolve(fileName)
                if (Files.exists(file)) {
                    val markdown = file.readText()
                    val document = markdownParser.parse(markdown)
                    val html = htmlRenderer.render(document)
                    sections.add(html)
                }
            }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("<meta charset='UTF-8'/>")
            appendLine("<title>Project Documentation</title>")
            appendLine("""
                <style>
                    body { font-family: Arial, sans-serif; margin: 40px; }
                    h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
                    h2 { color: #34495e; margin-top: 30px; }
                    h3 { color: #7f8c8d; }
                    code { background: #ecf0f1; padding: 2px 6px; border-radius: 3px; }
                    pre { background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 5px; overflow-x: auto; }
                    table { border-collapse: collapse; width: 100%; margin: 20px 0; }
                    th, td { border: 1px solid #bdc3c7; padding: 12px; text-align: left; }
                    th { background: #34495e; color: white; }
                    .page-break { page-break-after: always; }
                </style>
            """.trimIndent())
            appendLine("</head>")
            appendLine("<body>")

            sections.forEachIndexed { index, html ->
                appendLine(html)
                if (index < sections.size - 1) {
                    appendLine("<div class='page-break'></div>")
                }
            }

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun renderPDF(htmlContent: String, outputStream: OutputStream) {
        val builder = PdfRendererBuilder()
        builder.withHtmlContent(htmlContent, null)
        builder.toStream(outputStream)
        builder.run()
    }
}
