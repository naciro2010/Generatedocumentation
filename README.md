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

## 🧠 Comment Ça Marche Réellement

### Le Concept de Plugin

**Un plugin = un traducteur de code vers documentation**

Chaque plugin sait analyser un type de projet (Struts, EJB, PHP legacy, Express.js, etc.) et **extraire** les informations importantes:
- 📍 Endpoints (URLs accessibles)
- 💾 Entités (tables, modèles de données)
- ⚙️ Services (logique métier)
- 🔗 Relations entre composants

Le plugin produit un **IR (Intermediate Representation)** - un format normalisé que tous les plugins utilisent.

### Workflow Complet: Du Code à la Documentation

```
┌─────────────┐
│  Code       │  Votre dépôt Git (Struts, PHP, Spring, etc.)
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────────────────┐
│  PHASE 1: DÉTECTION                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Plugin 1 │  │ Plugin 2 │  │ Plugin 3 │  ...         │
│  │ Struts   │  │ PHP      │  │ Spring   │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
│       │             │             │                     │
│       ▼             ▼             ▼                     │
│    Score: 0.9    Score: 0.2    Score: 0.1              │
│    ✅ Struts!    ❌ Pas PHP    ❌ Pas Spring            │
└─────────────────────────────────────────────────────────┘
       │
       ▼ Plugin avec le meilleur score gagne
┌─────────────────────────────────────────────────────────┐
│  PHASE 2: EXTRACTION STRUCTURELLE (Plugin Struts)      │
│                                                         │
│  Input:                                                 │
│  ┌─────────────────────────────────────┐               │
│  │ class LoginAction extends Action {  │               │
│  │   public ActionForward execute(...) │               │
│  │     String user = req.getParam(...) │               │
│  │     ResultSet rs = conn.query(...)  │               │
│  │     ...                             │               │
│  │ }                                   │               │
│  └─────────────────────────────────────┘               │
│                                                         │
│  Output (IR):                                          │
│  ✅ Endpoint: POST /login.do                           │
│     Evidence: LoginAction.java:12                      │
│                                                         │
│  ✅ Service: LoginAction.execute()                     │
│     Evidence: LoginAction.java:12-45                   │
│                                                         │
│  ⚠️  Direct SQL Query detected                         │
│     Evidence: LoginAction.java:23                      │
│     ❗ Uncertainty: "Business logic unclear"           │
│     ❗ Uncertainty: "Need LLM to understand intent"    │
└─────────────────────────────────────────────────────────┘
       │
       ▼ Le plugin a trouvé des "uncertainties"
┌─────────────────────────────────────────────────────────┐
│  PHASE 3: ENRICHISSEMENT LLM (Critical!)               │
│                                                         │
│  Plugin → "J'ai trouvé un LoginAction mais je ne sais  │
│           pas ce qu'il fait vraiment au niveau métier"  │
│                                                         │
│  LLM reçoit:                                           │
│  - Le code source (LoginAction.java)                   │
│  - Le contexte (Struts 1.x, SQL direct)               │
│  - Question: "Quelle est la règle métier?"            │
│                                                         │
│  LLM répond:                                           │
│  📋 "Fonctionnalité: Authentification utilisateur"     │
│  📋 "Règles de gestion:"                               │
│     - Vérifie username/password contre base MySQL      │
│     - Crée une session si succès                       │
│     - Redirige vers /home.do si OK, /error.do si KO    │
│  📋 "Risques:"                                         │
│     - ⚠️ Potential SQL injection (pas de prepared stmt)│
│     - ⚠️ Password pas hashé (stocké en clair)          │
│  📋 "Schéma DB inféré:"                                │
│     - Table: users (id, username, password)            │
└─────────────────────────────────────────────────────────┘
       │
       ▼ IR + Enrichissement LLM combinés
┌─────────────────────────────────────────────────────────┐
│  PHASE 4: GÉNÉRATION DOCUMENTATION                      │
│                                                         │
│  Génère:                                               │
│  - architecture.md (diagrammes C4)                     │
│  - api.md (POST /login.do → LoginAction)               │
│  - features.md (🔐 Authentication avec règles métier)  │
│  - data-model.md (Table users avec schéma inféré)     │
│  - security.md (⚠️ SQL injection, passwords en clair)  │
│                                                         │
│  Tout avec des liens Evidence → Code source!           │
└─────────────────────────────────────────────────────────┘
```

### Pourquoi le LLM est Critique pour le Code Legacy

**Le problème du code ancien:**

| Avec un plugin seul | Avec Plugin + LLM |
|---------------------|-------------------|
| ✅ "Il y a une classe LoginAction" | ✅ "Il y a une classe LoginAction" |
| ✅ "Elle hérite de Action" | ✅ "C'est un Struts 1.x Action" |
| ✅ "Elle fait une requête SQL" | ✅ "Elle authentifie les utilisateurs contre MySQL" |
| ❌ On ne sait pas POURQUOI | ✅ "Règle: username + password vérifiés, session créée si OK" |
| ❌ On ne sait pas le schéma DB | ✅ "Table users(id, username, password)" |
| ❌ Pas de doc fonctionnelle | ✅ "Feature: User Authentication with sessions" |

**Le code legacy (Struts, EJB 2.x, PHP procédural) n'a pas:**
- ❌ Pas de commentaires
- ❌ Pas de doc Swagger/OpenAPI
- ❌ Logique métier mélangée avec SQL/HTML
- ❌ Noms de variables cryptiques (`$usr`, `rs`, `bean`)

**Le LLM comprend le POURQUOI en lisant le code:**
- ✅ Infère les règles métier à partir du code
- ✅ Devine le schéma DB à partir des requêtes SQL
- ✅ Comprend les workflows (login → session → redirect)
- ✅ Détecte les risques de sécurité

### Exemple Réel: Plugin PHP Legacy

**Code PHP procédural:**

```php
<?php
// login.php
session_start();
$user = $_POST['username'];
$pass = $_POST['password'];

$result = mysql_query("SELECT * FROM users WHERE username='$user' AND password='$pass'");
if(mysql_num_rows($result) > 0) {
    $_SESSION['logged_in'] = true;
    header("Location: dashboard.php");
} else {
    header("Location: error.php");
}
?>
```

**Ce que le PHPLegacyPlugin détecte:**

```kotlin
✅ Endpoint: /login.php (page PHP)
✅ Direct SQL query: SELECT * FROM users WHERE...
   Evidence: login.php:6
⚠️  SQL Injection risk (uses $_POST directly)
⚠️  Session management detected

❗ Uncertainties:
   - "Business rule behind this query unclear"
   - "Need LLM to extract database schema"
   - "Authentication flow requires LLM analysis"
```

**Ce que le LLM ajoute:**

```yaml
Functional Insight:
  Title: "User Authentication Feature"
  Description: |
    Authenticates users by checking credentials against MySQL database.
    Creates PHP session on success and redirects to dashboard.

  Business Rules:
    - User must provide username and password
    - Credentials verified against 'users' table
    - Session created if credentials match
    - Redirect to dashboard on success, error page on failure

  Security Risks:
    - 🔴 Critical: SQL Injection (direct POST to query)
    - 🔴 Critical: Passwords stored in plain text
    - 🟡 Medium: No CSRF protection

  Database Schema (inferred):
    Table: users
      - username: VARCHAR
      - password: VARCHAR (plain text!)

  User-Facing Feature:
    "Allows users to log into the system using username and password"
```

**Documentation générée:**

```markdown
# Feature: User Authentication

**Status:** ⚠️ Security Issues Detected

## Description
Allows users to log into the system using username and password.

## Implementation
- **File:** `login.php:1-12`
- **Type:** Procedural PHP
- **Database:** Direct MySQL queries

## Business Rules
1. User provides username + password via POST
2. System checks credentials against `users` table
3. If valid: create session, redirect to dashboard
4. If invalid: redirect to error page

## Security Analysis
🔴 **CRITICAL ISSUES FOUND:**
- SQL Injection vulnerability ([login.php:6](login.php#L6))
- Plain text password storage
- No CSRF protection

## Recommendations
- [ ] Use prepared statements (PDO)
- [ ] Hash passwords (bcrypt/Argon2)
- [ ] Add CSRF tokens
- [ ] Migrate to modern framework
```

### Les 8 Plugins Disponibles

| Plugin | Technologie | Cas d'Usage |
|--------|-------------|-------------|
| **JavaLegacyPlugin** | JBoss, Struts 1.x/2.x, EJB 2.x/3.x | Applications J2EE anciennes (2000-2010) |
| **PHPLegacyPlugin** | PHP procédural, ancien OOP | Sites PHP sans framework (~ 2005) |
| **JavaSpringPlugin** | Spring Boot, Spring MVC | Applications Java modernes |
| **NodeExpressPlugin** | Express.js, Node.js | API REST Node |
| **PythonFastAPIPlugin** | FastAPI, Pydantic | API Python modernes |
| **RubyRailsPlugin** | Ruby on Rails | Applications Rails |
| **GoPlugin** | Gin, net/http | Microservices Go |
| **RustPlugin** | Actix-web, Rocket | Services Rust |

**Focus sur le Legacy:**
- ✅ **JavaLegacyPlugin**: Analyse XML descriptors (struts-config.xml, ejb-jar.xml), détecte EJB 2.x avec Home/Remote interfaces
- ✅ **PHPLegacyPlugin**: Détecte SQL injection, sessions, formulaires HTML/PHP mélangés

### Créer Votre Propre Plugin

```kotlin
package io.docgen.plugins.impl

class MyFrameworkPlugin : AnalysisPlugin {
    override val name = "my-framework"

    // 1. Détection: retourne un score de confiance 0.0-1.0
    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        var score = 0.0

        // Check for framework-specific files
        if (Files.exists(projectPath.resolve("my-config.xml"))) {
            score += 0.5
        }

        // Check for typical code patterns
        val hasFrameworkCode = Files.walk(projectPath)
            .filter { it.extension == "java" }
            .anyMatch { it.readText().contains("import com.myframework") }

        if (hasFrameworkCode) score += 0.5

        return score.coerceAtMost(1.0)
    }

    // 2. Analyse: extrait l'IR + signale les incertitudes pour le LLM
    override fun analyze(projectPath: Path): PluginAnalysisResult {
        val endpoints = mutableListOf<EndpointIR>()
        val insights = mutableListOf<FunctionalInsight>()

        // Parcourir les fichiers
        Files.walk(projectPath).forEach { file ->
            val content = file.readText()

            // Détecter les patterns spécifiques
            val pattern = Regex("""@Route\("([^"]+)"\)""")
            pattern.findAll(content).forEach { match ->
                val path = match.groupValues[1]

                // Créer l'endpoint avec Evidence
                endpoints.add(EndpointIR(
                    id = path,
                    method = HttpMethod.GET,
                    path = path,
                    handler = file.name,
                    evidences = listOf(Evidence(
                        filePath = file.toString(),
                        startLine = lineNumber,
                        endLine = lineNumber,
                        symbol = "route-definition"
                    ))
                ))

                // Signaler au LLM: "Je ne comprends pas la logique métier"
                insights.add(FunctionalInsight(
                    category = "endpoint",
                    title = "Route: $path",
                    description = "HTTP endpoint detected",
                    confidence = "CERTAIN",
                    evidenceRefs = listOf("$file:$lineNumber"),
                    uncertainties = listOf(
                        "Business purpose requires LLM analysis",
                        "Request/response schema unknown"
                    )
                ))
            }
        }

        return PluginAnalysisResult(
            projectIR = ProjectIR(...),
            functionalInsights = insights
        )
    }
}
```

**Enregistrer le plugin:**

```kotlin
// apps/api/src/main/kotlin/io/docgen/api/config/AppConfig.kt
@Bean
fun analysisPlugins(): List<AnalysisPlugin> {
    return listOf(
        // ... plugins existants
        MyFrameworkPlugin()  // ← Ajouter ici
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
