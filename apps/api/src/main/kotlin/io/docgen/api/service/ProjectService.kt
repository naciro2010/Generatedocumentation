package io.docgen.api.service

import io.docgen.api.controller.ImportProjectRequest
import io.docgen.core.model.ImportType
import io.docgen.core.model.JobStatus
import io.docgen.core.model.ProjectStatus
import io.docgen.core.storage.DocumentationJobData
import io.docgen.core.storage.LocalFileStore
import io.docgen.core.storage.ProjectData
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Paths
import java.util.*

/**
 * Simplified ProjectService using file-based storage instead of PostgreSQL
 */
@Service
class ProjectServiceSimplified(
    @Value("\${docgen.storage-path:./storage}") private val storagePath: String
) {
    private val store = LocalFileStore(Paths.get(storagePath))

    fun importProject(request: ImportProjectRequest): String {
        val projectId = UUID.randomUUID().toString()

        val importType = when {
            request.gitUrl != null -> ImportType.GIT
            request.zipUrl != null -> ImportType.ZIP
            request.localPath != null -> ImportType.LOCAL
            else -> throw IllegalArgumentException("Must provide gitUrl, zipUrl, or localPath")
        }

        val project = ProjectData(
            id = projectId,
            name = request.name,
            repositoryUrl = request.gitUrl ?: request.zipUrl,
            importType = importType,
            storagePath = Paths.get(storagePath, projectId).toString(),
            status = ProjectStatus.PENDING
        )

        store.saveProject(project)
        return projectId
    }

    fun getProject(projectId: String): ProjectData {
        return store.getProject(projectId)
            ?: throw NoSuchElementException("Project not found: $projectId")
    }

    fun getAllProjects(): List<ProjectData> {
        return store.getAllProjects()
    }

    fun getProjectsByStatus(status: ProjectStatus): List<ProjectData> {
        return store.getProjectsByStatus(status)
    }

    fun updateProjectStatus(projectId: String, status: ProjectStatus, errorMessage: String? = null) {
        store.updateProjectStatus(projectId, status)
        if (errorMessage != null) {
            val project = getProject(projectId)
            store.saveProject(project.copy(errorMessage = errorMessage))
        }
    }

    fun generateDocumentation(projectId: String): String {
        val project = getProject(projectId)

        if (project.status != ProjectStatus.ANALYZED && project.status != ProjectStatus.COMPLETED) {
            throw IllegalStateException("Project must be analyzed before generating documentation")
        }

        val jobId = UUID.randomUUID().toString()
        val job = DocumentationJobData(
            id = jobId,
            projectId = projectId,
            status = JobStatus.PENDING
        )

        store.saveJob(job)
        return jobId
    }

    fun getJob(jobId: String): DocumentationJobData {
        return store.getJob(jobId)
            ?: throw NoSuchElementException("Job not found: $jobId")
    }

    fun updateJobStatus(jobId: String, status: JobStatus, errorMessage: String? = null, outputPath: String? = null) {
        store.updateJobStatus(jobId, status, errorMessage, outputPath)
    }

    fun getJobsByProject(projectId: String): List<DocumentationJobData> {
        return store.getJobsByProject(projectId)
    }

    fun deleteProject(projectId: String) {
        store.deleteProject(projectId)
    }
}
