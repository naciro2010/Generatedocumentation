# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Universal Documentation Generator** is a Kotlin/Spring Boot application that analyzes legacy code (Struts, EJB, PHP) and generates technical documentation with LLM enrichment. It uses a plugin system to detect frameworks and extract structured information into an Intermediate Representation (IR).

## Build Commands

### Complete Build
```bash
# Full build with tests
./gradlew build

# Build without tests (faster)
./gradlew build -x test

# Clean build
./gradlew clean build
```

### Module-Specific Builds
```bash
# Build individual libraries
./gradlew :libs:core:build
./gradlew :libs:parsing:build
./gradlew :libs:plugins:build
./gradlew :libs:llm:build
./gradlew :libs:docs:build

# Build Spring Boot applications
./gradlew :apps:api:build
./gradlew :apps:worker:build
```

## Running Tests

```bash
# All tests with coverage
./gradlew test

# Run tests for a specific module
./gradlew :libs:plugins:test

# Run a single test class
./gradlew :libs:core:test --tests "io.docgen.core.EntityIRTest"

# Run tests matching a pattern
./gradlew test --tests "*Legacy*"
```

## Development Setup

### Local Running (No Database Required!)
```bash
# No setup needed! Just run:

# Terminal 1: Start the API server
./gradlew :apps:api:bootRun

# Terminal 2: Start the worker (optional)
./gradlew :apps:worker:bootRun
```

**That's it!** Data is stored as JSON files in `./storage/` directory. No database setup required.

## Code Architecture

### Core Concepts

**Intermediate Representation (IR)**: The normalized data model that all plugins produce. Located in `libs/core/src/main/kotlin/io/docgen/core/ir/`. Key classes:
- `ProjectIR` - Root project model
- `ModuleIR` - Logical grouping of components
- `EndpointIR` - API endpoints (HTTP, RPC, etc.)
- `ServiceIR` - Business logic components
- `EntityIR` - Data models (classes, DB tables)
- `Evidence` - Proof linking assertions to source code

**Plugin System**: Each technology (Struts, PHP, Spring) has a plugin that:
1. Detects if a project uses that technology (scoring 0.0-1.0)
2. Analyzes the project and extracts IR
3. Reports "uncertainties" (areas needing LLM analysis)

Location: `libs/plugins/src/main/kotlin/io/docgen/plugins/impl/`

### Module Structure

```
libs/
├── core/         # Domain model (IR, Evidence, Assertion, Storage)
├── parsing/      # Technology fingerprinting + code extraction
├── plugins/      # Analysis plugins (JavaLegacy, PHPLegacy, Spring, etc.)
├── llm/          # LLM integration (OpenAI, Anthropic, Gemini, Bedrock)
└── docs/         # Markdown + Mermaid generation

apps/
├── api/          # Spring Boot REST API (file-based storage)
└── worker/       # Async processing (polling + multi-framework analysis)
```

### Key Package Structures

**libs/core**: Domain model - immutable data classes representing code structure
- No external dependencies (only Jackson for JSON)
- Value objects: `Evidence`, `Assertion<T>`, `Confidence`
- **Storage**: `ProjectStore` interface with `LocalFileStore` implementation (JSON files in `./storage/`)

**libs/parsing**: Detects frameworks and extracts basic code info
- `ProjectFingerprint` - Detects technologies
- Extractors - Regex/Tree-sitter based symbol extraction

**libs/plugins**: Framework-specific analysis
- `AnalysisPlugin` interface - All plugins implement this
- Each plugin produces `PluginAnalysisResult` containing IR + insights
- `FunctionalInsight` - Captures uncertainties for LLM

**libs/llm**: Optional LLM enrichment
- `LLMClient` interface with multiple implementations
- Providers: OpenAI, Anthropic, Gemini, AWS Bedrock, Mock (default)
- Used to resolve uncertainties from plugins

**libs/docs**: Documentation generation
- Markdown rendering with Mermaid diagrams
- MkDocs configuration generation
- Evidence-based documentation (links to source code)

## Important Architectural Decisions

**Evidence-Based Design** (ADR-003): Every assertion in documentation must point to source code location (file + line + symbol). This is critical for verifying claims. Use `Evidence` class with `filePath`, `startLine`, `endLine`, `symbol`.

**Normalized IR** (ADR-002): Different technologies (Struts vs Spring vs Express) all produce the same `ProjectIR` structure. This allows unified documentation generation.

**Mock LLM Default** (ADR-004): Application works without LLM using mock responses. Real LLM is optional via configuration. This allows full functionality without API keys.

**PostgreSQL + pgvector** (ADR-005): Single database with pgvector extension for embeddings (future semantic search). Chose Postgres over Neo4j for simplicity.

## Common Development Tasks

### Adding a New Plugin

1. Create class extending `AnalysisPlugin` in `libs/plugins/src/main/kotlin/io/docgen/plugins/impl/`
2. Implement `detect()` - return confidence score (0.0-1.0) based on framework-specific files/patterns
3. Implement `analyze()` - extract IR from project, flag uncertainties
4. Register in Spring config: `apps/api/src/main/kotlin/io/docgen/api/config/AppConfig.kt`
5. Add test: `libs/plugins/src/test/kotlin/io/docgen/plugins/impl/YourPluginTest.kt`

### Extracting Information from Source

Use the `Evidence` class to track where information comes from:
```kotlin
val evidence = Evidence(
    filePath = "src/LoginAction.java",
    startLine = 12,
    endLine = 45,
    symbol = "LoginAction.execute"
)
```

Always provide evidence for assertions. If you can't find proof, mark it as an `Uncertainty` instead.

### Adding LLM Analysis

1. Create `FunctionalInsight` with `uncertainties` list
2. Worker processes these and calls `LLMClient.analyze()`
3. LLM results are stored and used in documentation generation

The LLM is used to answer questions plugins can't answer alone:
- Business purpose of code
- Business rules and workflows
- Database schema inference
- Security risks
- User-facing features

### Working with File-Based Storage

All project and job data is stored as JSON files in the `./storage/` directory:

```
./storage/
├── projects/           # Project metadata (one JSON file per project)
│   ├── {projectId}.json
│   └── ...
└── jobs/              # Documentation generation jobs
    ├── {jobId}.json
    └── ...
```

To view stored data:
```bash
# List all projects
ls ./storage/projects/

# View a project's data
cat ./storage/projects/{projectId}.json | jq '.'

# View all jobs
cat ./storage/jobs/*.json | jq '.'
```

The storage implementation is in `libs/core/src/main/kotlin/io/docgen/core/storage/`:
- `ProjectStore.kt` - Storage interface
- `StorageModels.kt` - `ProjectData` and `DocumentationJobData` models

## Testing Patterns

```kotlin
// Unit tests for plugins
class JavaLegacyPluginTest {
    @Test
    fun `detect should identify Struts 1 x projects`() {
        val fingerprint = ProjectFingerprint(...)
        val score = plugin.detect(fingerprint, testProjectPath)
        assertThat(score).isGreaterThan(0.8)
    }
}

// Integration tests with file-based storage
class ProjectServiceTest {
    @Test
    fun `should save and retrieve projects from file storage`() {
        val store = LocalFileStore(tempDir.toPath())
        val project = ProjectData(...)
        store.saveProject(project)
        assertThat(store.getProject(project.id)).isEqualTo(project)
    }
}
```

## Configuration

### API Configuration
File: `apps/api/src/main/resources/application.yml`
- `docgen.storage-path` - Directory where project data is stored as JSON files (default: `./storage`)
- `docgen.max-project-size` - Max repo size in bytes
- `docgen.llm-enabled` - Enable LLM enrichment (optional)

### LLM Providers
Set via environment variables or `application.yml`:
- `LLM_PROVIDER` - "openai", "anthropic", "gemini", "bedrock", "mock"
- `LLM_API_KEY` - Provider API key
- `LLM_MODEL` - Model name (optional, uses default)

## Debugging Tips

**Find where plugins are selected**: `apps/worker/src/main/kotlin/.../AnalysisOrchestrator.kt` - runs detection and selects highest-scoring plugin

**Understand IR extraction**: Each plugin's `analyze()` method in `libs/plugins/src/main/kotlin/io/docgen/plugins/impl/`

**Trace LLM calls**: Check `libs/llm/src/main/kotlin/io/docgen/llm/client/` for client implementations

**Database debugging**: Use `SELECT * FROM code_symbols` and related tables in PostgreSQL

## Performance Considerations

- Projects are limited to 1GB max size (configurable)
- Plugin detection uses lightweight file scanning (fast)
- Analysis phase is blocking (can be slow for large projects)
- Documentation generation is done by async worker
- LLM calls are the slowest part and should be batched

## Documentation

- **README.md** - Complete feature overview and API reference
- **BUILD.md** - Detailed build instructions
- **ARCHITECTURE.md** - System design and data flow
- **QUICKSTART.md** - Code examples for common tasks
- **docs/AI_SETUP.md** - LLM provider configuration
