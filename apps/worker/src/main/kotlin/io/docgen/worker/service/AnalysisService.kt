package io.docgen.worker.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.docgen.core.model.*
import io.docgen.core.repository.*
import io.docgen.docs.generator.DocumentationGenerator
import io.docgen.parsing.detector.TechDetector
import io.docgen.parsing.extractor.CodeExtractor
import io.docgen.plugins.api.PluginRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

@Service
@Transactional
class AnalysisService(
    private val projectRepository: ProjectRepository,
    private val moduleRepository: ModuleRepository,
    private val codeSymbolRepository: CodeSymbolRepository,
    private val techDetector: TechDetector,
    private val pluginRegistry: PluginRegistry,
    private val codeExtractor: CodeExtractor,
    private val documentationGenerator: DocumentationGenerator,
    private val documentationJobRepository: DocumentationJobRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    fun analyzeProject(projectId: String) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { NoSuchElementException("Project not found: $projectId") }

        try {
            project.status = ProjectStatus.ANALYZING
            project.updatedAt = Instant.now()
            projectRepository.save(project)

            val projectPath = Paths.get(project.storagePath)

            // Step 1: Detect technologies
            logger.info("Detecting technologies for project $projectId")
            val fingerprint = techDetector.detect(projectPath)
            project.fingerprint = objectMapper.writeValueAsString(fingerprint)

            // Step 2: Count files and LOC
            val (fileCount, loc) = countFilesAndLines(projectPath)
            project.fileCount = fileCount
            project.linesOfCode = loc

            projectRepository.save(project)

            // Step 3: Find best plugin and analyze
            logger.info("Finding best plugin for project $projectId")
            val plugin = pluginRegistry.findBestPlugin(fingerprint, projectPath)

            if (plugin != null) {
                logger.info("Using plugin: ${plugin.name}")
                val analysisResult = plugin.analyze(projectPath)

                // Save IR to database
                saveProjectIR(project, analysisResult.projectIR)

                project.status = ProjectStatus.ANALYZED
                project.analyzedAt = Instant.now()
            } else {
                logger.warn("No suitable plugin found for project $projectId")
                project.status = ProjectStatus.ANALYZED // Still mark as analyzed, just with less data
            }

            project.updatedAt = Instant.now()
            projectRepository.save(project)

        } catch (e: Exception) {
            logger.error("Analysis failed for project $projectId", e)
            project.status = ProjectStatus.FAILED
            project.errorMessage = e.message
            project.updatedAt = Instant.now()
            projectRepository.save(project)
            throw e
        }
    }

    fun generateDocumentation(jobId: String) {
        val job = documentationJobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Job not found: $jobId") }

        try {
            job.status = JobStatus.RUNNING
            job.startedAt = Instant.now()
            documentationJobRepository.save(job)

            val project = projectRepository.findById(job.projectId)
                .orElseThrow { NoSuchElementException("Project not found: ${job.projectId}") }

            project.status = ProjectStatus.GENERATING_DOCS
            projectRepository.save(project)

            // Rebuild ProjectIR from database
            val projectIR = rebuildProjectIR(project)

            // Get functional insights (would be stored separately in real impl)
            val insights = emptyList<io.docgen.plugins.api.FunctionalInsight>()

            // Generate documentation
            val outputPath = Paths.get(project.storagePath, "output")
            val result = documentationGenerator.generate(projectIR, insights, outputPath)

            if (result.success) {
                job.status = JobStatus.COMPLETED
                job.outputPath = result.outputPath.toString()
                project.status = ProjectStatus.COMPLETED
            } else {
                job.status = JobStatus.FAILED
                job.errorMessage = result.errors.joinToString("; ")
                project.status = ProjectStatus.FAILED
                project.errorMessage = job.errorMessage
            }

            job.completedAt = Instant.now()
            documentationJobRepository.save(job)

            project.updatedAt = Instant.now()
            projectRepository.save(project)

        } catch (e: Exception) {
            logger.error("Documentation generation failed for job $jobId", e)
            job.status = JobStatus.FAILED
            job.errorMessage = e.message
            job.completedAt = Instant.now()
            documentationJobRepository.save(job)
            throw e
        }
    }

    private fun countFilesAndLines(projectPath: Path): Pair<Int, Long> {
        var fileCount = 0
        var totalLines = 0L

        Files.walk(projectPath)
            .filter { it.isRegularFile() }
            .filter { !it.toString().contains("/.") }
            .filter { !it.toString().contains("/node_modules/") }
            .filter { !it.toString().contains("/target/") }
            .filter { !it.toString().contains("/build/") }
            .forEach { file ->
                fileCount++
                try {
                    totalLines += Files.lines(file).count()
                } catch (e: Exception) {
                    // Skip binary files
                }
            }

        return fileCount to totalLines
    }

    private fun saveProjectIR(project: Project, projectIR: io.docgen.core.model.ProjectIR) {
        // Save modules
        projectIR.modules.forEach { moduleIR ->
            val module = Module(
                project = project,
                name = moduleIR.name,
                path = moduleIR.path,
                type = moduleIR.type
            )
            moduleRepository.save(module)

            // Save symbols
            moduleIR.services.forEach { service ->
                val symbol = CodeSymbol(
                    module = module,
                    name = service.name,
                    fqn = service.fqn,
                    symbolType = SymbolType.CLASS,
                    filePath = service.evidences.firstOrNull()?.filePath ?: "",
                    startLine = service.evidences.firstOrNull()?.startLine ?: 0
                )
                codeSymbolRepository.save(symbol)
            }
        }
    }

    private fun rebuildProjectIR(project: Project): io.docgen.core.model.ProjectIR {
        val modules = moduleRepository.findByProjectId(project.id)
        val fingerprint = objectMapper.readValue(project.fingerprint, io.docgen.core.model.ProjectFingerprint::class.java)

        return io.docgen.core.model.ProjectIR(
            projectId = project.id,
            name = project.name,
            fingerprint = fingerprint,
            modules = modules.map { module ->
                io.docgen.core.model.ModuleIR(
                    id = module.id ?: "",
                    name = module.name,
                    path = module.path,
                    type = module.type
                )
            }
        )
    }
}
