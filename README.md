# Universal Documentation Generator

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Universal Documentation Generator** est une application Kotlin/Spring Boot qui génère automatiquement la documentation technique et fonctionnelle d'un dépôt de code, quelle que soit sa technologie.

## 🎯 Objectifs

- **Documentation Technique**: architecture, modules, dépendances, API, modèle de données, diagrammes Mermaid
- **Documentation Fonctionnelle**: concepts métier, cas d'usage, règles de gestion, mapping feature→code
- **Evidence-Based**: toute assertion importante pointe vers une preuve (fichier + lignes + symbole)
- **Multi-Technologies**: supporte Node.js, Python, Java/Kotlin, et extensible via plugins

## 🏗️ Architecture

Monorepo Gradle multi-modules:

```
├── apps/
│   ├── api/          # REST API (Spring Boot)
│   └── worker/       # Background jobs processor
├── libs/
│   ├── core/         # Domain model + IR + entities
│   ├── parsing/      # Code parsing (Tree-sitter + extractors)
│   ├── plugins/      # Plugin system + built-in plugins
│   ├── llm/          # LLM integration (interface + mock)
│   └── docs/         # Documentation generation (Markdown + Mermaid)
└── infra/
    ├── docker-compose.yml
    └── Dockerfile
```

## 🚀 Quick Start

### Prérequis

- **Java 21+** (ou JDK 17 minimum)
- **Docker & Docker Compose**
- **Gradle 8.11+** (wrapper inclus)

### Lancement avec Docker Compose

```bash
# 1. Démarrer les services
cd infra
docker-compose up -d

# 2. Vérifier que tout fonctionne
curl http://localhost:8080/actuator/health
```

L'API est disponible sur `http://localhost:8080`

### Build & Run Local

```bash
# Build complet
./gradlew build

# Démarrer seulement Postgres
cd infra
docker-compose up -d postgres

# Lancer l'API
./gradlew :apps:api:bootRun

# Dans un autre terminal: lancer le worker
./gradlew :apps:worker:bootRun
```

## 📚 Usage

### 1. Importer un projet

```bash
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-project",
    "gitUrl": "https://github.com/example/repo"
  }'
```

Réponse:
```json
{
  "projectId": "abc-123",
  "status": "PENDING"
}
```

### 2. Vérifier le statut

```bash
curl http://localhost:8080/v1/projects/abc-123/status
```

Réponse:
```json
{
  "projectId": "abc-123",
  "status": "ANALYZED",
  "progress": 80,
  "message": "Analysis completed"
}
```

### 3. Générer la documentation

```bash
curl -X POST http://localhost:8080/v1/projects/abc-123/generate-docs
```

Réponse:
```json
{
  "jobId": "job-456",
  "status": "PENDING"
}
```

### 4. Récupérer la documentation

La documentation générée est disponible dans `./storage/{projectId}/output/docs/`

Structure:
```
docs/
├── overview.md          # Vue d'ensemble du projet
├── architecture.md      # Architecture + diagrammes C4
├── api.md              # Référence API
├── data-model.md       # Modèle de données + ER diagram
├── features.md         # Features détectées
├── diagrams/           # Diagrammes Mermaid
└── evidence/
    └── evidence-index.md  # Mapping assertions → preuves
```

## 🔌 Plugins

L'application inclut **6 plugins** prêts à l'emploi:

1. **Node.js/Express** - Détecte routes, middleware, endpoints
2. **Python/FastAPI** - Détecte routes FastAPI, modèles Pydantic
3. **Java/Spring Boot** - Détecte controllers, entities, services
4. **Ruby/Rails** - Détecte actions, routes RESTful, models ActiveRecord
5. **Go** - Détecte HTTP handlers, Gin routes, structs
6. **Rust** - Détecte Actix-web/Rocket routes, structs

### Créer un plugin personnalisé

```kotlin
class MyCustomPlugin : AnalysisPlugin {
    override val name = "my-plugin"
    override val description = "Description"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        // Return confidence 0.0-1.0
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        // Extract IR from project
    }
}
```

Enregistrer dans `AppConfig`:
```kotlin
@Bean
fun analysisPlugins(): List<AnalysisPlugin> {
    return listOf(
        NodeExpressPlugin(),
        PythonFastAPIPlugin(),
        JavaSpringPlugin(),
        MyCustomPlugin()  // Ajouter ici
    )
}
```

## 🧪 Tests

```bash
# Tous les tests
./gradlew test

# Tests d'intégration avec Testcontainers
./gradlew :apps:api:test

# Tests unitaires d'un module
./gradlew :libs:core:test
```

## 🔧 Configuration

Fichier `apps/api/src/main/resources/application.yml`:

```yaml
docgen:
  storage-path: ./storage
  max-project-size: 1000000000  # 1GB
  llm-enabled: false

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/docgen
    username: docgen
    password: docgen
```

## 📊 API Reference

### Endpoints

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/v1/projects/import` | Importer un projet |
| GET | `/v1/projects/{id}` | Détails du projet |
| GET | `/v1/projects/{id}/status` | Statut du projet |
| POST | `/v1/projects/{id}/generate-docs` | Générer la documentation |
| GET | `/v1/projects` | Lister tous les projets |
| GET | `/v1/search/{projectId}?query=...` | **NEW** Recherche sémantique |
| GET | `/v1/export/{projectId}/pdf` | **NEW** Export PDF |

**UI Web**: Accédez à `http://localhost:8080` pour une interface graphique!

Documentation OpenAPI/Swagger disponible sur: `http://localhost:8080/swagger-ui.html`

## 🧩 Modèle de Données

### Tables principales

- **projects** - Projets importés
- **modules** - Modules détectés
- **code_symbols** - Symboles de code (classes, fonctions)
- **code_relations** - Relations entre symboles (appels, dépendances)
- **evidences** - Preuves pour assertions
- **documentation_jobs** - Jobs de génération de docs

Voir le schéma complet: `apps/api/src/main/resources/db/migration/V1__initial_schema.sql`

## 🎨 Evidence-Based Documentation

Principe fondamental: **toute assertion doit être prouvée**.

```kotlin
// Assertion avec preuve
val endpoint = Assertion.certain(
    EndpointInfo(method = "POST", path = "/users"),
    Evidence(
        filePath = "src/routes.ts",
        startLine = 42,
        symbol = "createUser"
    )
)

// Assertion incertaine (pas de preuve)
val feature = Assertion.uncertain(
    "User authentication feature",
    uncertainties = listOf(
        "Business purpose unclear without LLM analysis",
        "User-facing capability unknown"
    )
)
```

## 🔮 LLM Integration (Optional)

L'application peut fonctionner **sans LLM** (mode fallback avec mock).

### Providers supportés:
- **OpenAI** (GPT-4 Turbo)
- **Anthropic** (Claude 3.5 Sonnet)
- **Mock** (par défaut, sans API key)

### Configuration:

```yaml
docgen:
  llm:
    enabled: true
    provider: openai  # ou anthropic, mock
    api-key: ${LLM_API_KEY}
    model: gpt-4-turbo-preview  # optionnel
```

Ou via variables d'environnement:
```bash
export LLM_API_KEY="sk-..."
export LLM_MODEL="gpt-4-turbo-preview"
```

Le LLM est utilisé pour:
- Déduction de fonctionnalités métier
- Identification de règles de gestion
- Génération de descriptions user-facing
- Analyse sémantique avancée

## 🛠️ Développement

### Structure du Code

```kotlin
// Domain model (libs/core)
data class ProjectIR(...)
data class ModuleIR(...)
data class EndpointIR(...)

// Plugin API (libs/plugins)
interface AnalysisPlugin {
    fun detect(...): Double
    fun analyze(...): PluginAnalysisResult
}

// Documentation generation (libs/docs)
interface DocumentationGenerator {
    fun generate(...): GenerationResult
}
```

### Ajouter une nouvelle techno

1. Créer un plugin dans `libs/plugins/src/main/kotlin/io/docgen/plugins/impl/`
2. Implémenter `AnalysisPlugin`
3. Ajouter des patterns de détection
4. Extraire vers IR normalisé
5. Tester avec un projet sample

## 📈 Next Steps

- [ ] Support Neo4j pour graphe de code (optionnel)
- [ ] Plugin Ruby/Rails
- [ ] Plugin Go
- [ ] Intégration Tree-sitter native (via JNI)
- [ ] Support LSP pour analyse sémantique avancée
- [ ] Export documentation en PDF
- [ ] UI web pour visualisation
- [ ] Recherche sémantique avec pgvector embeddings

## 🤝 Contributing

Les contributions sont bienvenues!

1. Fork le projet
2. Créer une branche feature (`git checkout -b feature/amazing-feature`)
3. Commit les changements (`git commit -m 'Add amazing feature'`)
4. Push vers la branche (`git push origin feature/amazing-feature`)
5. Ouvrir une Pull Request

## 📄 License

Apache License 2.0 - voir le fichier [LICENSE](LICENSE)

## 🙏 Credits

- **Spring Boot** - Framework
- **Kotlin** - Language
- **PostgreSQL + pgvector** - Database
- **Mermaid** - Diagrammes
- **MkDocs** - Documentation rendering
- **Testcontainers** - Integration testing

---

**Made with ❤️ using Kotlin & Spring Boot**
