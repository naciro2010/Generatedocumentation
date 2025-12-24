package io.docgen.api.controller

import io.docgen.api.service.ProjectService
import io.docgen.core.model.ProjectStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/v1/projects")
class ProjectController(
    private val projectService: ProjectService
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
            ProjectStatus.ANALYZING -> "Analyzing project structure and code..."
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
    val createdAt: Instant,
    val updatedAt: Instant,
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
    val createdAt: Instant
)
