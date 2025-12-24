package io.docgen.worker.job

import io.docgen.core.model.JobStatus
import io.docgen.core.model.ProjectStatus
import io.docgen.core.repository.DocumentationJobRepository
import io.docgen.core.repository.ProjectRepository
import io.docgen.worker.service.AnalysisService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Background job processor.
 * In production, this would use a proper job queue (RabbitMQ, SQS, etc.).
 */
@Component
class ProjectProcessor(
    private val projectRepository: ProjectRepository,
    private val documentationJobRepository: DocumentationJobRepository,
    private val analysisService: AnalysisService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    fun processPendingProjects() {
        val pendingProjects = projectRepository.findByStatus(ProjectStatus.PENDING)

        pendingProjects.forEach { project ->
            try {
                logger.info("Processing project ${project.id}")
                // In real impl, clone git repo or extract zip here
                project.status = ProjectStatus.IMPORTED
                projectRepository.save(project)

                // Trigger analysis
                analysisService.analyzeProject(project.id)
            } catch (e: Exception) {
                logger.error("Failed to process project ${project.id}", e)
            }
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun processPendingDocJobs() {
        val pendingJobs = documentationJobRepository.findByStatus(JobStatus.PENDING)

        pendingJobs.forEach { job ->
            try {
                logger.info("Processing documentation job ${job.id}")
                analysisService.generateDocumentation(job.id)
            } catch (e: Exception) {
                logger.error("Failed to process doc job ${job.id}", e)
            }
        }
    }
}
