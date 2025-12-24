package io.docgen.api.service

import io.docgen.api.controller.ImportProjectRequest
import io.docgen.core.model.*
import io.docgen.core.repository.DocumentationJobRepository
import io.docgen.core.repository.ProjectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Paths
import java.util.*

@Service
@Transactional
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val documentationJobRepository: DocumentationJobRepository
) {

    fun importProject(request: ImportProjectRequest): String {
        val projectId = UUID.randomUUID().toString()

        val importType = when {
            request.gitUrl != null -> ImportType.GIT
            request.zipUrl != null -> ImportType.ZIP
            request.localPath != null -> ImportType.LOCAL
            else -> throw IllegalArgumentException("Must provide gitUrl, zipUrl, or localPath")
        }

        val project = Project(
            id = projectId,
            name = request.name,
            repositoryUrl = request.gitUrl ?: request.zipUrl,
            importType = importType,
            storagePath = Paths.get("./storage", projectId).toString(),
            status = ProjectStatus.PENDING
        )

        projectRepository.save(project)

        // TODO: Trigger async import job

        return projectId
    }

    fun getProject(projectId: String): Project {
        return projectRepository.findById(projectId)
            .orElseThrow { NoSuchElementException("Project not found: $projectId") }
    }

    fun getAllProjects(): List<Project> {
        return projectRepository.findAll()
    }

    fun getProjectsByStatus(status: ProjectStatus): List<Project> {
        return projectRepository.findByStatus(status)
    }

    fun generateDocumentation(projectId: String): String {
        val project = getProject(projectId)

        if (project.status != ProjectStatus.ANALYZED && project.status != ProjectStatus.COMPLETED) {
            throw IllegalStateException("Project must be analyzed before generating documentation")
        }

        val jobId = UUID.randomUUID().toString()
        val job = DocumentationJob(
            id = jobId,
            projectId = projectId,
            status = JobStatus.PENDING
        )

        documentationJobRepository.save(job)

        // TODO: Trigger async doc generation job

        return jobId
    }
}
