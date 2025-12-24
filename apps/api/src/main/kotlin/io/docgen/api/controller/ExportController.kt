package io.docgen.api.controller

import io.docgen.core.repository.ProjectRepository
import io.docgen.docs.pdf.PDFExporter
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Paths

@RestController
@RequestMapping("/v1/export")
class ExportController(
    private val projectRepository: ProjectRepository,
    private val pdfExporter: PDFExporter
) {

    @GetMapping("/{projectId}/pdf")
    fun exportPDF(@PathVariable projectId: String): ResponseEntity<Resource> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { NoSuchElementException("Project not found: $projectId") }

        val docsPath = Paths.get(project.storagePath, "output", "docs")
        val pdfPath = Paths.get(project.storagePath, "output", "documentation.pdf")

        pdfExporter.exportToPDF(docsPath, pdfPath)

        val resource = FileSystemResource(pdfPath)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documentation.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(resource)
    }
}
