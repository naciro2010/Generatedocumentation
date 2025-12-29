# Quick Start - Universal Documentation Generator

## Concept Simple

Ce tool analyse automatiquement du code legacy (Struts, EJB, PHP) et génère de la documentation avec l'aide d'un LLM.

## Workflow en 3 Étapes

```kotlin
// 1. Choisir le bon plugin
val plugin = JavaLegacyPlugin() // ou PHPLegacyPlugin()

// 2. Analyser le projet
val result = plugin.analyze(Paths.get("/path/to/legacy-project"))

// 3. Enrichir avec LLM
val llm = LLMClientFactory.create("openai", apiKey = "sk-...")
val enriched = llm.analyzeBusinessLogic(result)
```

## Exemple Complet: Analyser un Projet Struts

```kotlin
package io.docgen.example

import io.docgen.plugins.impl.legacy.JavaLegacyPlugin
import io.docgen.llm.LLMClientFactory
import java.nio.file.Paths

fun main() {
    // 1. Configuration
    val projectPath = Paths.get("./samples/struts-app")
    val llmApiKey = System.getenv("OPENAI_API_KEY") ?: "sk-..."

    println("📂 Analyse du projet: $projectPath")

    // 2. Détection
    val plugin = JavaLegacyPlugin()
    val fingerprint = detectTechnologies(projectPath)
    val confidence = plugin.detect(fingerprint, projectPath)

    println("✅ Struts détecté avec confiance: ${confidence * 100}%")

    // 3. Extraction structurelle
    val analysis = plugin.analyze(projectPath)

    println("\n📊 Résultats de l'analyse:")
    println("- Modules: ${analysis.projectIR.modules.size}")
    println("- Endpoints: ${analysis.projectIR.modules.flatMap { it.endpoints }.size}")
    println("- Services: ${analysis.projectIR.modules.flatMap { it.services }.size}")

    // 4. Afficher les incertitudes (zones qui nécessitent LLM)
    println("\n❓ Incertitudes détectées:")
    analysis.functionalInsights
        .filter { it.uncertainties.isNotEmpty() }
        .forEach { insight ->
            println("- ${insight.title}")
            insight.uncertainties.forEach { u ->
                println("  └─ $u")
            }
        }

    // 5. Enrichissement LLM
    println("\n🤖 Enrichissement avec LLM...")
    val llmClient = LLMClientFactory.create(
        provider = "openai",
        apiKey = llmApiKey,
        model = "gpt-4-turbo-preview"
    )

    // Pour chaque incertitude, demander au LLM
    val enrichedInsights = analysis.functionalInsights.map { insight ->
        if (insight.uncertainties.isNotEmpty()) {
            val prompt = buildPrompt(insight, analysis.projectIR)
            val businessLogic = llmClient.analyzeCode(prompt)
            insight.copy(description = businessLogic)
        } else {
            insight
        }
    }

    // 6. Génération de documentation
    println("\n📝 Génération de la documentation...")
    val docGenerator = DocumentationGenerator()
    docGenerator.generate(
        projectIR = analysis.projectIR,
        insights = enrichedInsights,
        outputPath = Paths.get("./output/docs")
    )

    println("✅ Documentation générée dans ./output/docs/")
}

fun detectTechnologies(projectPath: Path): ProjectFingerprint {
    // Scan des fichiers pour détecter les technos
    val detector = FileBasedDetector()
    return detector.analyze(projectPath)
}

fun buildPrompt(insight: FunctionalInsight, projectIR: ProjectIR): String {
    return """
        Analyze this legacy code component:

        Component: ${insight.title}
        Type: ${insight.category}
        Evidence: ${insight.evidenceRefs.joinToString()}

        Questions:
        ${insight.uncertainties.joinToString("\n") { "- $it" }}

        Please provide:
        1. Business purpose of this component
        2. Business rules implemented
        3. User-facing functionality
        4. Security risks if any

        Format as structured JSON.
    """.trimIndent()
}
```

## Exemple: Plugin PHP Legacy

```kotlin
fun analyzePHPProject() {
    val plugin = PHPLegacyPlugin()
    val projectPath = Paths.get("./samples/old-php-site")

    // Analyse
    val result = plugin.analyze(projectPath)

    // Le plugin détecte automatiquement:
    // ✅ SQL injection vulnerabilities
    // ✅ Session management
    // ✅ Form handling
    // ✅ Direct SQL queries

    // Afficher les vulnérabilités
    result.warnings.forEach { warning ->
        println("⚠️ $warning")
    }

    // Les "uncertainties" indiquent où le LLM doit intervenir
    val needsLLM = result.functionalInsights.filter {
        it.uncertainties.isNotEmpty()
    }

    println("\n${needsLLM.size} composants nécessitent l'analyse LLM")
}
```

## Configuration LLM

### OpenAI (GPT-4)

```kotlin
val llm = LLMClientFactory.create(
    provider = "openai",
    apiKey = "sk-...",
    model = "gpt-4-turbo-preview"
)
```

### Anthropic (Claude)

```kotlin
val llm = LLMClientFactory.create(
    provider = "anthropic",
    apiKey = "sk-ant-...",
    model = "claude-3-5-sonnet-20241022"
)
```

### Google Gemini (GRATUIT!)

```kotlin
val llm = LLMClientFactory.create(
    provider = "gemini",
    apiKey = "...", // API key gratuite depuis Google AI Studio
    model = "gemini-pro"
)
```

### AWS Bedrock

```kotlin
val llm = LLMClientFactory.create(
    provider = "bedrock",
    awsRegion = "us-east-1",
    model = "anthropic.claude-3-sonnet"
)
// Utilise les credentials AWS par défaut (~/.aws/credentials)
```

### Mock (pour tests)

```kotlin
val llm = LLMClientFactory.create(provider = "mock")
// Retourne des réponses fictives, pas besoin d'API key
```

## Utilisation Sans LLM

Vous pouvez utiliser le système SANS LLM pour obtenir l'analyse structurelle:

```kotlin
val plugin = JavaLegacyPlugin()
val result = plugin.analyze(projectPath)

// Vous obtenez quand même:
// ✅ Liste des endpoints
// ✅ Structure des services
// ✅ Détection des patterns (Struts Actions, EJB, etc.)
// ✅ Evidence (liens vers le code source)

// Mais SANS:
// ❌ Compréhension des règles métier
// ❌ Description fonctionnelle
// ❌ Inférence du schéma DB
```

## Plugins Disponibles

| Plugin | Technologie | Cas d'Usage |
|--------|-------------|-------------|
| `JavaLegacyPlugin` | JBoss, Struts, EJB 2.x/3.x | Apps J2EE 2000-2010 |
| `PHPLegacyPlugin` | PHP procédural, vieux OOP | Sites PHP sans framework |
| `JavaSpringPlugin` | Spring Boot, Spring MVC | Apps Java modernes |
| `NodeExpressPlugin` | Express.js | API REST Node |
| `PythonFastAPIPlugin` | FastAPI | API Python |
| `RubyRailsPlugin` | Rails | Apps Ruby |
| `GoPlugin` | Gin, net/http | Microservices Go |
| `RustPlugin` | Actix, Rocket | Services Rust |

## Créer Votre Plugin

```kotlin
class MyFrameworkPlugin : AnalysisPlugin {
    override val name = "my-framework"
    override val description = "Analyzes MyFramework applications"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        // Retourne un score 0.0-1.0
        return if (Files.exists(projectPath.resolve("my-config.xml"))) 0.8 else 0.0
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val insights = mutableListOf<FunctionalInsight>()

        // Parse le code, extrait les infos...
        Files.walk(projectPath).forEach { file ->
            // Analyse...
        }

        return PluginAnalysisResult(
            projectIR = ProjectIR(...),
            functionalInsights = insights
        )
    }
}
```

## Documentation Générée

Après l'analyse, vous obtenez:

```
output/docs/
├── overview.md              # Vue d'ensemble
├── architecture.md          # Diagrammes C4 + architecture
├── api.md                  # Liste des endpoints avec Evidence
├── features.md             # Features métier (enrichies par LLM)
├── security.md             # Vulnérabilités détectées
├── data-model.md           # Schéma DB (inféré par LLM)
└── evidence/
    └── evidence-index.md   # Mapping assertions → code source
```

Chaque assertion dans la doc pointe vers le code source:

```markdown
## Endpoint: POST /login.do

**Handler:** `LoginAction.execute()` ([LoginAction.java:12-45](../src/LoginAction.java#L12))

**Business Logic:** Authenticates users against MySQL database
([LoginAction.java:23](../src/LoginAction.java#L23))

⚠️ **Security Risk:** SQL Injection detected
([LoginAction.java:23](../src/LoginAction.java#L23))
```

## Next Steps

1. **Build le projet**: Voir [BUILD.md](BUILD.md)
2. **Configurer un LLM**: Choisir OpenAI, Anthropic, Gemini ou Bedrock
3. **Analyser votre code legacy**
4. **Générer la documentation**

## Support

Pour plus de détails, voir:
- [README.md](README.md) - Documentation complète
- [docs/AI_SETUP.md](docs/AI_SETUP.md) - Configuration des providers LLM
- [README_EN.md](README_EN.md) - English documentation
