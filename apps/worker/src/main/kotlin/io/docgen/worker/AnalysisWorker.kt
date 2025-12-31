package io.docgen.worker

import io.docgen.api.service.ProjectServiceSimplified
import io.docgen.core.model.ProjectStatus
import io.docgen.core.model.JobStatus
import io.docgen.core.parsing.ProjectFingerprinter
import io.docgen.llm.validator.ExtractionValidator
import io.docgen.plugins.orchestrator.MultiFrameworkOrchestrator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

/**
 * Simplified worker that:
 * 1. Polls for PENDING projects
 * 2. Runs ALL applicable plugins (multi-framework)
 * 3. Validates extraction with LLM
 * 4. Generates documentation
 */
@SpringBootApplication
@EnableScheduling
class AnalysisWorkerApplication

fun main(args: Array<String>) {
    runApplication<AnalysisWorkerApplication>(*args)
}

@Component
class AnalysisWorker(
    private val projectService: ProjectServiceSimplified,
    private val multiFrameworkOrchestrator: MultiFrameworkOrchestrator,
    private val fingerprinter: ProjectFingerprinter,
    private val extractionValidator: ExtractionValidator
) {

    /**
     * Poll for PENDING projects every 5 seconds
     */
    @Scheduled(fixedDelayString = "\${docgen.worker.poll-interval-ms:5000}")
    fun processPendingProjects() {
        val pendingProjects = projectService.getProjectsByStatus(ProjectStatus.PENDING)

        if (pendingProjects.isNotEmpty()) {
            println("🔍 Found ${pendingProjects.size} pending projects")
        }

        for (project in pendingProjects) {
            processProject(project.id)
        }
    }

    /**
     * Process a single project through the entire pipeline
     */
    private fun processProject(projectId: String) {
        println("\n=== Processing project: $projectId ===")

        try {
            val project = projectService.getProject(projectId)
            val projectPath = Paths.get(project.storagePath)

            // Step 1: Import/Clone repository
            println("📥 Importing project files...")
            projectService.updateProjectStatus(projectId, ProjectStatus.IMPORTING)
            // TODO: Import from Git/ZIP (implement in separate module)

            projectService.updateProjectStatus(projectId, ProjectStatus.IMPORTED)

            // Step 2: Analyze with ALL applicable plugins
            println("🔬 Analyzing with multi-framework orchestrator...")
            projectService.updateProjectStatus(projectId, ProjectStatus.ANALYZING)

            val analysisTime = measureTimeMillis {
                val fingerprint = fingerprinter.detectTechnologies(projectPath)
                val orchestrationResult = multiFrameworkOrchestrator.analyzeWithMultiplePlugins(
                    fingerprint,
                    projectPath
                )

                println("✅ Plugins used: ${orchestrationResult.appliedPlugins}")
                println("📊 Found ${orchestrationResult.projectIR.modules.size} modules")
                println("🔗 Found ${orchestrationResult.projectIR.modules.flatMap { it.endpoints }.size} endpoints")

                if (orchestrationResult.mergeReport.warnings.isNotEmpty()) {
                    println("⚠️  Merge warnings:")
                    orchestrationResult.mergeReport.warnings.forEach { println("   - $it") }
                }

                // Step 3: Validate extraction with LLM (optional)
                if (shouldValidateWithLLM()) {
                    println("🤖 Validating extraction with LLM...")
                    val validationResult = extractionValidator.validateExtraction(
                        projectIR = orchestrationResult.projectIR,
                        projectPath = projectPath,
                        criticalFiles = listOf("app.js", "main.py", "pom.xml")
                    )

                    if (!validationResult.isValid) {
                        println("⚠️  Found issues:")
                        validationResult.warnings.forEach { println("   - $it") }
                        // Could merge missing endpoints here
                    } else {
                        println("✅ Extraction validated (confidence: ${validationResult.confidence})")
                    }
                }

                // Save the analysis result
                // TODO: Store projectIRJson in the project metadata
            }

            println("✨ Analysis completed in ${analysisTime}ms")
            projectService.updateProjectStatus(projectId, ProjectStatus.ANALYZED)

        } catch (e: Exception) {
            println("❌ Error processing project: ${e.message}")
            e.printStackTrace()
            projectService.updateProjectStatus(projectId, ProjectStatus.FAILED, e.message)
        }
    }

    /**
     * Should we validate with LLM?
     * (Check if enabled in config)
     */
    private fun shouldValidateWithLLM(): Boolean {
        // TODO: Read from configuration
        return false // Disabled by default for cost
    }
}

/**
 * Example: How to use the new simplified architecture programmatically
 */
object ExampleUsage {

    fun example() {
        // 1. Create orchestrator with all plugins
        // val orchestrator = MultiFrameworkOrchestrator(plugins)

        // 2. Analyze project
        // val result = orchestrator.analyzeWithMultiplePlugins(fingerprint, projectPath)

        // 3. Check which plugins found something
        // println("Used plugins: ${result.appliedPlugins}")
        // println("Total endpoints: ${result.projectIR.modules.flatMap { it.endpoints }.size}")

        // 4. Merge results are already done!
        // result.projectIR is already merged from all plugins

        // 5. Optional: Validate with LLM
        // val validation = validator.validateExtraction(result.projectIR, projectPath)
        // if (!validation.isValid) {
        //     // Handle missing endpoints
        // }

        // 6. Generate documentation
        // docGenerator.generate(result.projectIR, outputPath)

        println("✅ Processing pipeline complete!")
    }
}
