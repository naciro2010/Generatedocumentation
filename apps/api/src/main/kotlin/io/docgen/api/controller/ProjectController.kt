package io.docgen.api.controller

import io.docgen.api.service.ProjectServiceSimplified
import io.docgen.core.model.ProjectStatus
import io.docgen.plugins.orchestrator.MultiFrameworkOrchestrator
import io.docgen.core.parsing.ProjectFingerprinter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.nio.file.Paths

@RestController
@RequestMapping("/v1/projects")
class ProjectControllerSimplified(
    private val projectService: ProjectServiceSimplified,
    private val multiFrameworkOrchestrator: MultiFrameworkOrchestrator,
    private val fingerprinter: ProjectFingerprinter
) {

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun importProject(@Valid @RequestBody request: ImportProjectRequest): ImportProjectResponse {
        val projectId = projectService.importProject(request)
        return ImportProjectResponse(projectId = projectId, status = "PENDING")
    }

    @GetMapping("/{projectId}")
    fun getProject(@PathVariable projectId: String): ProjectResponse {
        val project = projectService.getProject(projectId)
        return ProjectResponse(
            id = project.id,
            name = project.name,
            status = project.status.name,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            repositoryUrl = project.repositoryUrl,
            linesOfCode = project.linesOfCode,
            fileCount = project.fileCount,
            errorMessage = project.errorMessage
        )
    }

    @GetMapping("/{projectId}/status")
    fun getProjectStatus(@PathVariable projectId: String): ProjectStatusResponse {
        val project = projectService.getProject(projectId)
        return ProjectStatusResponse(
            projectId = project.id,
            status = project.status.name,
            progress = calculateProgress(project.status),
            message = getStatusMessage(project.status),
            errorMessage = project.errorMessage
        )
    }

    @PostMapping("/{projectId}/generate-docs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun generateDocs(@PathVariable projectId: String): GenerateDocsResponse {
        val jobId = projectService.generateDocumentation(projectId)
        return GenerateDocsResponse(jobId = jobId, status = "PENDING")
    }

    @GetMapping
    fun listProjects(
        @RequestParam(required = false) status: String?
    ): List<ProjectSummary> {
        val projects = if (status != null) {
            projectService.getProjectsByStatus(ProjectStatus.valueOf(status))
        } else {
            projectService.getAllProjects()
        }

        return projects.map {
            ProjectSummary(
                id = it.id,
                name = it.name,
                status = it.status.name,
                createdAt = it.createdAt
            )
        }
    }

    /**
     * NEW: Analyze project with MULTI-FRAMEWORK support
     * This is called by the worker to start analysis
     */
    @PostMapping("/{projectId}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun analyzeProject(@PathVariable projectId: String): AnalysisResponse {
        val project = projectService.getProject(projectId)
        val projectPath = Paths.get(project.storagePath)

        return try {
            // Fingerprint the project
            val fingerprint = fingerprinter.detectTechnologies(projectPath)

            // Run ALL applicable plugins (not just best one)
            val orchestrationResult = multiFrameworkOrchestrator.analyzeWithMultiplePlugins(
                fingerprint,
                projectPath
            )

            projectService.updateProjectStatus(projectId, ProjectStatus.ANALYZED)

            AnalysisResponse(
                projectId = projectId,
                status = "SUCCESS",
                appliedPlugins = orchestrationResult.appliedPlugins,
                moduleCount = orchestrationResult.projectIR.modules.size,
                endpointCount = orchestrationResult.projectIR.modules.flatMap { it.endpoints }.size,
                warnings = orchestrationResult.mergeReport.warnings,
                message = "Analysis complete with ${orchestrationResult.appliedPlugins.size} plugins"
            )
        } catch (e: Exception) {
            projectService.updateProjectStatus(projectId, ProjectStatus.FAILED, e.message)
            AnalysisResponse(
                projectId = projectId,
                status = "FAILED",
                appliedPlugins = emptyList(),
                moduleCount = 0,
                endpointCount = 0,
                warnings = listOf(e.message ?: "Unknown error"),
                message = "Analysis failed"
            )
        }
    }

    private fun calculateProgress(status: ProjectStatus): Int =
        when (status) {
            ProjectStatus.PENDING -> 0
            ProjectStatus.IMPORTING -> 20
            ProjectStatus.IMPORTED -> 40
            ProjectStatus.ANALYZING -> 60
            ProjectStatus.ANALYZED -> 80
            ProjectStatus.GENERATING_DOCS -> 90
            ProjectStatus.COMPLETED -> 100
            ProjectStatus.FAILED -> 0
        }

    private fun getStatusMessage(status: ProjectStatus): String =
        when (status) {
            ProjectStatus.PENDING -> "Project queued for import"
            ProjectStatus.IMPORTING -> "Importing project files..."
            ProjectStatus.IMPORTED -> "Project files imported successfully"
            ProjectStatus.ANALYZING -> "Analyzing project structure (multi-framework mode)..."
            ProjectStatus.ANALYZED -> "Analysis completed"
            ProjectStatus.GENERATING_DOCS -> "Generating documentation..."
            ProjectStatus.COMPLETED -> "Documentation generation completed"
            ProjectStatus.FAILED -> "Processing failed"
        }
}

// DTOs
data class ImportProjectRequest(
    @field:NotBlank val name: String,
    val gitUrl: String? = null,
    val zipUrl: String? = null,
    val localPath: String? = null
)

data class ImportProjectResponse(
    val projectId: String,
    val status: String
)

data class ProjectResponse(
    val id: String,
    val name: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val repositoryUrl: String?,
    val linesOfCode: Long,
    val fileCount: Int,
    val errorMessage: String?
)

data class ProjectStatusResponse(
    val projectId: String,
    val status: String,
    val progress: Int,
    val message: String,
    val errorMessage: String?
)

data class GenerateDocsResponse(
    val jobId: String,
    val status: String
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val status: String,
    val createdAt: Long
)

data class AnalysisResponse(
    val projectId: String,
    val status: String,
    val appliedPlugins: List<String>,
    val moduleCount: Int,
    val endpointCount: Int,
    val warnings: List<String>,
    val message: String
)
