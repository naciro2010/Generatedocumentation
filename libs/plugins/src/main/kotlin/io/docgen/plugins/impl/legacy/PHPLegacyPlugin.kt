package io.docgen.plugins.impl.legacy

import io.docgen.core.domain.Evidence
import io.docgen.core.model.*
import io.docgen.plugins.api.AnalysisPlugin
import io.docgen.plugins.api.FunctionalInsight
import io.docgen.plugins.api.FunctionalRule
import io.docgen.plugins.api.PluginAnalysisResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * Plugin pour PHP legacy (procédural et ancien POO).
 *
 * Détecte:
 * - Fonctions PHP procédurales
 * - Classes PHP anciennes (sans namespace)
 * - Includes/requires
 * - Requêtes SQL directes
 * - Sessions et cookies
 * - Formulaires HTML/PHP mélangés
 */
class PHPLegacyPlugin : AnalysisPlugin {
    override val name = "php-legacy"
    override val description = "Analyze legacy PHP applications (procedural & old OOP)"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("PHP")) {
            score += 0.5
        }

        // Détection code legacy (pas de composer.json = probablement ancien)
        val hasComposer = Files.exists(projectPath.resolve("composer.json"))
        if (!hasComposer) {
            score += 0.3 // Pas de composer = probablement legacy
        }

        // Fichiers typiques du legacy
        val legacyFiles = listOf("index.php", "config.php", "functions.php", "db.php")
        legacyFiles.forEach { file ->
            if (Files.exists(projectPath.resolve(file))) {
                score += 0.05
            }
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val entities = mutableListOf<EntityIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()
        val sqlQueries = mutableListOf<String>()

        Files.walk(projectPath)
            .filter { it.extension == "php" }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                analyzePHPFile(content, relativePath, endpoints, entities, services, insights, sqlQueries)
            }

        // Générer insights sur les patterns détectés
        if (sqlQueries.isNotEmpty()) {
            insights.add(FunctionalInsight(
                category = "data-access",
                title = "Direct SQL Queries Detected",
                description = "Found ${sqlQueries.size} direct SQL queries - potential SQL injection risk",
                confidence = "CERTAIN",
                evidenceRefs = sqlQueries.take(10),
                uncertainties = listOf(
                    "Need LLM analysis to understand database schema",
                    "Need LLM analysis to identify business rules in SQL logic"
                )
            ))
        }

        val module = ModuleIR(
            id = "main",
            name = "main",
            path = ".",
            type = ModuleType.API,
            services = services,
            endpoints = endpoints,
            entities = entities
        )

        return PluginAnalysisResult(
            projectIR = ProjectIR(
                projectId = projectPath.fileName.toString(),
                name = projectPath.fileName.toString(),
                fingerprint = ProjectFingerprint(
                    languages = mapOf("PHP" to 1.0),
                    frameworks = mapOf("Legacy PHP" to 1.0),
                    buildTools = emptyMap(),
                    detectedFiles = listOf("index.php", "config.php")
                ),
                modules = listOf(module)
            ),
            functionalInsights = insights,
            warnings = listOf(
                "Legacy PHP code detected - requires LLM for business logic analysis",
                "Direct SQL queries found - potential security risk",
                "Mixed HTML/PHP code - difficult to analyze automatically",
                "Consider using LLM to extract business rules and data model"
            )
        )
    }

    private fun analyzePHPFile(
        content: String,
        relativePath: String,
        endpoints: MutableList<EndpointIR>,
        entities: MutableList<EntityIR>,
        services: MutableList<ServiceIR>,
        insights: MutableList<FunctionalInsight>,
        sqlQueries: MutableList<String>
    ) {
        val lineNum = { text: String, matchStart: Int ->
            text.substring(0, matchStart).count { it == '\n' } + 1
        }

        // Détecter les pages PHP (endpoints)
        if (relativePath.endsWith(".php") && !relativePath.contains("include") && !relativePath.contains("function")) {
            // C'est probablement une page accessible
            val pageName = relativePath.substringAfterLast("/").removeSuffix(".php")

            endpoints.add(EndpointIR(
                id = pageName,
                method = HttpMethod.GET,
                path = "/$relativePath",
                handler = relativePath,
                evidences = listOf(Evidence(relativePath, 1, 1, "php-page"))
            ))

            insights.add(FunctionalInsight(
                category = "endpoint",
                title = "PHP Page: $pageName",
                description = "Legacy PHP page - likely mixes HTML, PHP logic, and database access",
                confidence = "PROBABLE",
                evidenceRefs = listOf("$relativePath:1"),
                uncertainties = listOf(
                    "Exact functionality requires LLM analysis",
                    "May handle both GET and POST requests"
                )
            ))
        }

        // Détecter les fonctions
        val functionPattern = Regex("""function\s+(\w+)\s*\([^)]*\)""")
        functionPattern.findAll(content).forEach { match ->
            val functionName = match.groupValues[1]
            val line = lineNum(content, match.range.first)

            services.add(ServiceIR(
                id = functionName,
                name = functionName,
                fqn = "$relativePath::$functionName",
                type = "function",
                evidences = listOf(Evidence(relativePath, line, line, functionName))
            ))
        }

        // Détecter les classes (ancien style)
        val classPattern = Regex("""class\s+(\w+)""")
        classPattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val line = lineNum(content, match.range.first)

            entities.add(EntityIR(
                id = className,
                name = className,
                fqn = className,
                evidences = listOf(Evidence(relativePath, line, line, className))
            ))
        }

        // Détecter les requêtes SQL directes
        val sqlPatterns = listOf(
            Regex("""mysql_query\s*\(\s*["']([^"']+)["']"""),
            Regex("""mysqli_query\s*\([^,]+,\s*["']([^"']+)["']"""),
            Regex("""\$(?:db|conn|pdo)->query\s*\(\s*["']([^"']+)["']"""),
            Regex("""pg_query\s*\([^,]+,\s*["']([^"']+)["']""")
        )

        sqlPatterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                val query = match.groupValues[1]
                val line = lineNum(content, match.range.first)

                sqlQueries.add("$relativePath:$line")

                // Détecter le type de requête
                val queryType = when {
                    query.startsWith("SELECT", ignoreCase = true) -> "read"
                    query.startsWith("INSERT", ignoreCase = true) -> "create"
                    query.startsWith("UPDATE", ignoreCase = true) -> "update"
                    query.startsWith("DELETE", ignoreCase = true) -> "delete"
                    else -> "unknown"
                }

                insights.add(FunctionalInsight(
                    category = "database-operation",
                    title = "SQL $queryType: ${query.take(50)}...",
                    description = "Direct SQL query - potential SQL injection if not properly escaped",
                    confidence = "CERTAIN",
                    evidenceRefs = listOf("$relativePath:$line"),
                    uncertainties = listOf(
                        "Need LLM to understand business rule behind this query",
                        "Need LLM to extract database schema from queries"
                    )
                ))
            }
        }

        // Détecter les sessions
        if (content.contains("session_start()") || content.contains("$_SESSION")) {
            insights.add(FunctionalInsight(
                category = "authentication",
                title = "Session Management",
                description = "Uses PHP sessions - likely for authentication or state management",
                confidence = "PROBABLE",
                evidenceRefs = listOf(relativePath),
                uncertainties = listOf("Exact authentication flow requires LLM analysis")
            ))
        }

        // Détecter les formulaires
        val formPattern = Regex("""<form[^>]*action=["']([^"']+)["']""")
        formPattern.findAll(content).forEach { match ->
            val action = match.groupValues[1]
            val line = lineNum(content, match.range.first)

            insights.add(FunctionalInsight(
                category = "user-interaction",
                title = "Form submission to: $action",
                description = "HTML form found - user input point",
                confidence = "CERTAIN",
                evidenceRefs = listOf("$relativePath:$line"),
                uncertainties = listOf(
                    "Form fields and validation require LLM analysis",
                    "Business process behind form requires LLM understanding"
                )
            ))
        }
    }

    override fun functionalRules(): List<FunctionalRule> {
        return listOf(
            FunctionalRule(
                name = "sql-injection-risk",
                pattern = Regex("""mysql_query.*\$_(GET|POST|REQUEST)"""),
                category = "security",
                description = "Potential SQL injection vulnerability"
            ),
            FunctionalRule(
                name = "xss-risk",
                pattern = Regex("""echo\s+\$_(GET|POST|REQUEST)|print\s+\$_(GET|POST|REQUEST)"""),
                category = "security",
                description = "Potential XSS vulnerability"
            ),
            FunctionalRule(
                name = "file-upload",
                pattern = Regex("""\$_FILES|move_uploaded_file"""),
                category = "feature",
                description = "File upload functionality"
            )
        )
    }
}
