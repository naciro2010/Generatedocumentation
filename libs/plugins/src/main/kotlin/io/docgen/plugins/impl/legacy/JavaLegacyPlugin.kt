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
 * Plugin pour applications Java legacy: JBoss, Struts, EJB 2.x/3.x
 *
 * Détecte et analyse:
 * - Struts Actions (1.x et 2.x)
 * - EJB 2.x (Session Beans, Entity Beans avec descripteurs XML)
 * - EJB 3.x (annotations)
 * - JBoss configuration (jboss.xml, jboss-web.xml)
 * - Servlets classiques
 */
class JavaLegacyPlugin : AnalysisPlugin {
    override val name = "java-legacy"
    override val description = "Analyze legacy Java applications (JBoss, Struts, EJB)"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        if (fingerprint.languages.containsKey("Java")) {
            score += 0.3
        }

        // Détection Struts
        listOf("struts-config.xml", "struts.xml").forEach { file ->
            if (Files.exists(projectPath.resolve(file)) ||
                Files.walk(projectPath, 3).anyMatch { it.fileName.toString() == file }) {
                score += 0.3
            }
        }

        // Détection EJB
        listOf("ejb-jar.xml", "jboss.xml", "jboss-ejb3.xml").forEach { file ->
            if (Files.walk(projectPath, 5).anyMatch { it.fileName.toString() == file }) {
                score += 0.2
            }
        }

        // Détection JBoss
        if (Files.walk(projectPath, 3).anyMatch {
            it.fileName.toString() in listOf("jboss-web.xml", "jboss-deployment-structure.xml")
        }) {
            score += 0.2
        }

        return score.coerceAtMost(1.0)
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val entities = mutableListOf<EntityIR>()
        val services = mutableListOf<ServiceIR>()
        val insights = mutableListOf<FunctionalInsight>()

        // Analyser les fichiers Java
        Files.walk(projectPath)
            .filter { it.extension == "java" }
            .filter { !it.toString().contains("/target/") }
            .filter { !it.toString().contains("/build/") }
            .forEach { file ->
                analyzeJavaFile(file, projectPath, endpoints, entities, services, insights)
            }

        // Analyser les descripteurs XML
        analyzeXMLDescriptors(projectPath, endpoints, entities, services, insights)

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
                    languages = mapOf("Java" to 1.0),
                    frameworks = mapOf("Legacy Java" to 1.0),
                    buildTools = emptyMap(),
                    detectedFiles = listOf("struts-config.xml", "ejb-jar.xml")
                ),
                modules = listOf(module)
            ),
            functionalInsights = insights,
            warnings = listOf(
                "Legacy code detected - consider LLM analysis for business logic understanding",
                "EJB 2.x detected - consider migration to modern framework",
                "Struts 1.x detected - known security vulnerabilities"
            )
        )
    }

    private fun analyzeJavaFile(
        file: Path,
        projectPath: Path,
        endpoints: MutableList<EndpointIR>,
        entities: MutableList<EntityIR>,
        services: MutableList<ServiceIR>,
        insights: MutableList<FunctionalInsight>
    ) {
        val content = file.readText()
        val relativePath = projectPath.relativize(file).toString()

        // Détecter Struts Action
        val strutsActionPattern = Regex("""class\s+(\w+)\s+extends\s+(?:Action|DispatchAction|LookupDispatchAction)""")
        strutsActionPattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

            services.add(ServiceIR(
                id = className,
                name = className,
                fqn = extractPackage(content) + ".$className",
                type = "struts-action",
                evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
            ))

            insights.add(FunctionalInsight(
                category = "legacy-framework",
                title = "Struts Action: $className",
                description = "Legacy Struts 1.x Action class - handles HTTP requests through XML configuration",
                confidence = "CERTAIN",
                evidenceRefs = listOf("$relativePath:$lineNum"),
                uncertainties = listOf(
                    "Exact mapping requires struts-config.xml analysis",
                    "Business logic may be spread across multiple layers"
                )
            ))
        }

        // Détecter EJB 2.x (Session Bean, Entity Bean)
        val ejb2Pattern = Regex("""(?:implements\s+SessionBean|implements\s+EntityBean|extends\s+EntityBean)""")
        if (ejb2Pattern.containsMatchIn(content)) {
            val classPattern = Regex("""class\s+(\w+)""")
            val className = classPattern.find(content)?.groupValues?.get(1) ?: "Unknown"
            val lineNum = content.lines().indexOfFirst { it.contains("implements SessionBean") || it.contains("implements EntityBean") } + 1

            services.add(ServiceIR(
                id = className,
                name = className,
                fqn = extractPackage(content) + ".$className",
                type = "ejb2-bean",
                evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
            ))

            insights.add(FunctionalInsight(
                category = "legacy-framework",
                title = "EJB 2.x Bean: $className",
                description = "Legacy EJB 2.x component - uses XML deployment descriptors and home/remote interfaces",
                confidence = "CERTAIN",
                evidenceRefs = listOf("$relativePath:$lineNum"),
                uncertainties = listOf(
                    "Full EJB configuration requires ejb-jar.xml analysis",
                    "Transaction and security settings in XML descriptors",
                    "Business logic understanding requires LLM analysis"
                )
            ))
        }

        // Détecter EJB 3.x (annotations)
        val ejb3Pattern = Regex("""@(Stateless|Stateful|MessageDriven|Entity)""")
        ejb3Pattern.findAll(content).forEach { match ->
            val annotation = match.groupValues[1]
            val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1
            val classPattern = Regex("""class\s+(\w+)""")
            val className = classPattern.find(content.substring(match.range.last))?.groupValues?.get(1) ?: "Unknown"

            if (annotation == "Entity") {
                entities.add(EntityIR(
                    id = className,
                    name = className,
                    fqn = extractPackage(content) + ".$className",
                    evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
                ))
            } else {
                services.add(ServiceIR(
                    id = className,
                    name = className,
                    fqn = extractPackage(content) + ".$className",
                    type = "ejb3-$annotation",
                    evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
                ))
            }

            insights.add(FunctionalInsight(
                category = "ejb",
                title = "EJB 3.x $annotation: $className",
                description = "EJB 3.x component with annotation-based configuration",
                confidence = "CERTAIN",
                evidenceRefs = listOf("$relativePath:$lineNum")
            ))
        }

        // Détecter Servlets
        val servletPattern = Regex("""class\s+(\w+)\s+extends\s+HttpServlet""")
        servletPattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

            // Chercher les méthodes doGet/doPost
            val doGetPattern = Regex("""protected\s+void\s+doGet\s*\(""")
            val doPostPattern = Regex("""protected\s+void\s+doPost\s*\(""")

            if (doGetPattern.containsMatchIn(content)) {
                endpoints.add(EndpointIR(
                    id = "$className.doGet",
                    method = HttpMethod.GET,
                    path = "/servlet/$className", // Approximation
                    handler = "$relativePath:$lineNum",
                    evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
                ))
            }

            if (doPostPattern.containsMatchIn(content)) {
                endpoints.add(EndpointIR(
                    id = "$className.doPost",
                    method = HttpMethod.POST,
                    path = "/servlet/$className",
                    handler = "$relativePath:$lineNum",
                    evidences = listOf(Evidence(relativePath, lineNum, lineNum, className))
                ))
            }

            insights.add(FunctionalInsight(
                category = "servlet",
                title = "Servlet: $className",
                description = "Classic Java Servlet - exact URL mapping requires web.xml analysis",
                confidence = "PROBABLE",
                evidenceRefs = listOf("$relativePath:$lineNum"),
                uncertainties = listOf("URL mapping defined in web.xml")
            ))
        }
    }

    private fun analyzeXMLDescriptors(
        projectPath: Path,
        endpoints: MutableList<EndpointIR>,
        entities: MutableList<EntityIR>,
        services: MutableList<ServiceIR>,
        insights: MutableList<FunctionalInsight>
    ) {
        // Analyser struts-config.xml
        Files.walk(projectPath)
            .filter { it.fileName.toString() == "struts-config.xml" }
            .forEach { file ->
                val content = file.readText()
                val relativePath = projectPath.relativize(file).toString()

                // Extraire les actions Struts
                val actionPattern = Regex("""<action\s+path="([^"]+)".*?type="([^"]+)".*?>""")
                actionPattern.findAll(content).forEach { match ->
                    val path = match.groupValues[1]
                    val actionClass = match.groupValues[2]
                    val lineNum = content.substring(0, match.range.first).count { it == '\n' } + 1

                    endpoints.add(EndpointIR(
                        id = path,
                        method = HttpMethod.POST, // Struts default
                        path = path + ".do",
                        handler = actionClass,
                        evidences = listOf(Evidence(relativePath, lineNum, lineNum, "action-mapping"))
                    ))

                    insights.add(FunctionalInsight(
                        category = "struts-mapping",
                        title = "Struts Action Mapping: $path",
                        description = "Maps HTTP request '$path.do' to Action class $actionClass",
                        confidence = "CERTAIN",
                        evidenceRefs = listOf("$relativePath:$lineNum")
                    ))
                }
            }
    }

    private fun extractPackage(content: String): String {
        val packagePattern = Regex("""package\s+([\w.]+);""")
        return packagePattern.find(content)?.groupValues?.get(1) ?: "unknown"
    }

    override fun functionalRules(): List<FunctionalRule> {
        return listOf(
            FunctionalRule(
                name = "struts-validation",
                pattern = Regex("""validation\.xml|validator-rules\.xml"""),
                category = "validation",
                description = "Struts validation rules"
            ),
            FunctionalRule(
                name = "ejb-transaction",
                pattern = Regex("""@TransactionAttribute|<trans-attribute>"""),
                category = "transaction",
                description = "EJB transaction management"
            ),
            FunctionalRule(
                name = "jboss-security",
                pattern = Regex("""<security-domain>|@SecurityDomain"""),
                category = "security",
                description = "JBoss security configuration"
            )
        )
    }
}
