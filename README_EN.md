# 📚 Universal Documentation Generator

> **Automatically create documentation for any code project** - Node.js, Python, Java, Ruby, Go, or Rust!

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 🎯 What Does It Do?

This app reads your code and creates two types of documentation:

1. **Technical Docs**: Architecture, APIs, database models, diagrams
2. **Functional Docs**: What your app does, business rules, features

**Key Feature**: Every fact in the docs links to the actual code (with file + line number)!

## ✨ Features

- ✅ **6 Programming Languages**: Node.js, Python, Java/Kotlin, Ruby, Go, Rust
- ✅ **Smart Analysis**: Finds endpoints, models, routes automatically
- ✅ **Beautiful UI**: Web interface at `http://localhost:8080`
- ✅ **Export PDF**: Download docs as professional PDF
- ✅ **Search Code**: Semantic search inside your project
- ✅ **AI Powered**: Optional AI integration (OpenAI, Anthropic, Google Gemini, AWS Bedrock)
- ✅ **Graph Database**: Optional Neo4j for code relationships
- ✅ **Evidence-Based**: All facts have proof (no guessing!)

## 🚀 Quick Start (5 Minutes)

### Step 1: Install Docker

You need [Docker Desktop](https://www.docker.com/products/docker-desktop) installed.

### Step 2: Start the App

```bash
# Clone this repo
git clone <your-repo>
cd Generatedocumentation

# Start everything
cd infra
docker-compose up -d

# Wait 30 seconds, then open your browser
open http://localhost:8080
```

That's it! The app is running.

### Step 3: Import a Project

**Option A: Use the Web UI**

1. Go to `http://localhost:8080`
2. Fill the form:
   - **Project Name**: "my-app"
   - **Git URL**: "https://github.com/yourname/yourproject"
3. Click "Import Project"
4. Wait for analysis to complete
5. Click "Generate Docs"
6. Click "Export PDF" to download

**Option B: Use the API**

```bash
# Import a project
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-awesome-app",
    "gitUrl": "https://github.com/user/repo"
  }'

# Response: {"projectId": "abc-123", "status": "PENDING"}

# Check status
curl http://localhost:8080/v1/projects/abc-123/status

# Generate docs
curl -X POST http://localhost:8080/v1/projects/abc-123/generate-docs

# Download PDF
curl http://localhost:8080/v1/export/abc-123/pdf -o documentation.pdf
```

## 🤖 AI Integration (Optional)

The app works **without AI** by default. But you can add AI for smarter analysis!

### Supported AI Providers

| Provider | Model | Setup |
|----------|-------|-------|
| **OpenAI** | GPT-4 Turbo | Need API key from [OpenAI](https://platform.openai.com) |
| **Anthropic** | Claude 3.5 Sonnet | Need API key from [Anthropic](https://console.anthropic.com) |
| **Google** | Gemini Pro | Need API key from [Google AI Studio](https://makersuite.google.com) |
| **AWS** | Bedrock (Claude) | Need AWS credentials + Bedrock access |
| **Mock** | No AI | Default - works offline |

### How to Enable AI

#### Option 1: OpenAI (Recommended for Beginners)

```bash
# 1. Get your API key from https://platform.openai.com/api-keys

# 2. Set environment variable
export LLM_API_KEY="sk-proj-YOUR-KEY-HERE"

# 3. Restart the app
cd infra
docker-compose restart api
```

#### Option 2: Google Gemini

```bash
# 1. Get your API key from https://makersuite.google.com/app/apikey

# 2. Set environment variables
export LLM_API_KEY="your-gemini-api-key"
export LLM_PROVIDER="gemini"

# 3. Restart
docker-compose restart api
```

#### Option 3: AWS Bedrock

```bash
# 1. Set up AWS credentials
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_REGION="us-east-1"

# 2. Configure Bedrock
export LLM_PROVIDER="bedrock"
export LLM_MODEL="anthropic.claude-3-sonnet-20240229-v1:0"

# 3. Restart
docker-compose restart api
```

#### Option 4: Anthropic Claude

```bash
export LLM_API_KEY="sk-ant-YOUR-KEY"
export LLM_PROVIDER="anthropic"
docker-compose restart api
```

### Configuration File

Edit `apps/api/src/main/resources/application.yml`:

```yaml
docgen:
  llm:
    enabled: true
    provider: gemini  # openai, anthropic, gemini, bedrock, mock
    api-key: ${LLM_API_KEY:}
    model: ${LLM_MODEL:gemini-pro}
```

## 📖 How It Works

```
1. You give it a Git URL
       ↓
2. App downloads the code
       ↓
3. Detects the language (Node? Python? Java?)
       ↓
4. Finds routes, APIs, database models
       ↓
5. Creates markdown documentation
       ↓
6. Generates diagrams with Mermaid
       ↓
7. You get beautiful docs (Markdown + PDF)
```

## 🔌 Supported Technologies

### Languages & Frameworks

| Language | Frameworks | What We Detect |
|----------|-----------|----------------|
| **JavaScript/TypeScript** | Express, Fastify | Routes, middleware, handlers |
| **Python** | FastAPI, Flask, Django | Endpoints, Pydantic models |
| **Java/Kotlin** | Spring Boot | Controllers, entities, services |
| **Ruby** | Rails | Actions, models, routes |
| **Go** | Gin, net/http | HTTP handlers, structs |
| **Rust** | Actix-web, Rocket | Routes, Serde models |

### More Coming Soon

Want another language? Create a plugin! (See docs below)

## 🌐 API Endpoints

| Method | URL | What It Does |
|--------|-----|--------------|
| POST | `/v1/projects/import` | Start importing a project |
| GET | `/v1/projects` | List all projects |
| GET | `/v1/projects/{id}` | Get project details |
| GET | `/v1/projects/{id}/status` | Check import progress |
| POST | `/v1/projects/{id}/generate-docs` | Create documentation |
| GET | `/v1/search/{id}?query=...` | Search in code |
| GET | `/v1/export/{id}/pdf` | Download PDF |

## 📂 What You Get

After processing, you'll have:

```
docs/
├── overview.md           # Project summary
├── architecture.md       # System architecture + diagrams
├── api.md               # All endpoints documented
├── data-model.md        # Database schema + ER diagrams
├── features.md          # Features we found
└── evidence/
    └── evidence-index.md # Proof for every claim
```

## 🎨 Web Interface

![Web UI](https://via.placeholder.com/800x400/667eea/ffffff?text=Beautiful+Web+UI)

Features:
- Dashboard with all projects
- Real-time status updates
- One-click doc generation
- Download PDF directly
- Search functionality
- Mobile-friendly design

## 🛠️ Development

### Build from Source

```bash
# Requirements: Java 21+, Docker

# Clone
git clone <repo>
cd Generatedocumentation

# Build
./gradlew build

# Run locally (without Docker)
# 1. Start PostgreSQL
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=docgen \
  -e POSTGRES_USER=docgen \
  -e POSTGRES_PASSWORD=docgen \
  pgvector/pgvector:pg16

# 2. Run API
./gradlew :apps:api:bootRun

# 3. Run Worker (in another terminal)
./gradlew :apps:worker:bootRun
```

### Run Tests

```bash
# All tests
./gradlew test

# Only API tests
./gradlew :apps:api:test

# Only core tests
./gradlew :libs:core:test
```

### Create a Plugin

Want to add support for PHP, C#, or another language?

```kotlin
// In libs/plugins/src/main/kotlin/io/docgen/plugins/impl/

class MyLanguagePlugin : AnalysisPlugin {
    override val name = "my-language"
    override val description = "Analyze MyLanguage projects"

    override fun detect(fingerprint: ProjectFingerprint, projectPath: Path): Double {
        // Return 0.0-1.0 (how sure you are this is your language)
        return if (Files.exists(projectPath.resolve("myconfig.xml"))) 0.9 else 0.0
    }

    override fun analyze(projectPath: Path): PluginAnalysisResult {
        // Find routes, models, etc.
        // Return normalized IR (Intermediate Representation)
    }
}
```

Then register it in `AppConfig.kt`:

```kotlin
@Bean
fun analysisPlugins(): List<AnalysisPlugin> {
    return listOf(
        NodeExpressPlugin(),
        PythonFastAPIPlugin(),
        // ... other plugins ...
        MyLanguagePlugin() // Add yours here!
    )
}
```

## 🐳 Docker Compose Services

```yaml
services:
  postgres:   # Main database (stores projects, symbols)
  neo4j:      # Graph database (optional, for code relationships)
  api:        # REST API + Web UI
  worker:     # Background jobs (import, analyze, generate docs)
```

## 🔍 Search Feature

Find anything in your code:

```bash
# Search for "authentication"
curl "http://localhost:8080/v1/search/project-id?query=authentication&limit=10"

# Response:
{
  "query": "authentication",
  "results": [
    {
      "symbolName": "loginUser",
      "symbolType": "FUNCTION",
      "filePath": "src/auth/login.js",
      "line": 42,
      "similarity": 0.87,
      "rank": 1
    }
  ]
}
```

## 🔐 Security Notes

- **Secrets Detection**: App won't expose API keys or passwords
- **No Code Execution**: Only reads code, never runs it
- **Local First**: Everything stays on your machine
- **Size Limit**: Max 1GB per project (configurable)

## 📊 System Requirements

**Minimum**:
- 4GB RAM
- 10GB disk space
- Docker Desktop

**Recommended**:
- 8GB RAM
- 50GB disk space
- SSD drive

## 🆘 Troubleshooting

### App won't start

```bash
# Check Docker is running
docker ps

# Check logs
docker-compose logs api
docker-compose logs worker

# Restart everything
docker-compose down
docker-compose up -d
```

### Import stuck at "PENDING"

```bash
# Check worker logs
docker-compose logs worker

# Restart worker
docker-compose restart worker
```

### Can't access http://localhost:8080

```bash
# Check if port is already used
lsof -i :8080  # Mac/Linux
netstat -ano | findstr :8080  # Windows

# Change port in docker-compose.yml
ports:
  - "8081:8080"  # Use 8081 instead
```

### AI not working

```bash
# Check environment variables are set
echo $LLM_API_KEY
echo $LLM_PROVIDER

# Check API logs for errors
docker-compose logs api | grep LLM
```

## 📚 More Examples

### Import Local Folder

```bash
# Copy your code to storage
cp -r /path/to/myproject ./storage/my-local-project

# Use API with localPath
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-local-project",
    "localPath": "/app/storage/my-local-project"
  }'
```

### Import ZIP File

```bash
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "zipped-project",
    "zipUrl": "https://example.com/code.zip"
  }'
```

## 🎓 Advanced Topics

### Neo4j Graph Analysis

```bash
# Access Neo4j browser
open http://localhost:7474

# Login: neo4j / docgenneo4j

# Query code graph
MATCH (n:CodeNode)-[r:CALLS]->(m:CodeNode)
WHERE n.projectId = 'your-project-id'
RETURN n, r, m
LIMIT 100
```

### Custom LLM Prompts

Edit `libs/llm/src/main/kotlin/io/docgen/llm/api/LLMClient.kt` to customize AI prompts.

### Performance Tuning

```yaml
# In application.yml
docgen:
  max-project-size: 2000000000  # 2GB

spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # More connections
```

## 🤝 Contributing

Pull requests welcome!

1. Fork this repo
2. Create a branch: `git checkout -b my-feature`
3. Make changes and test
4. Commit: `git commit -m 'Add my feature'`
5. Push: `git push origin my-feature`
6. Open a Pull Request

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) file

## 🙋 FAQ

**Q: Is it free?**
A: Yes! The app is free. AI providers (OpenAI, etc.) may charge for API usage.

**Q: Does it support private repos?**
A: Yes, if you provide auth credentials in the Git URL or clone locally first.

**Q: Can I use it for commercial projects?**
A: Yes, Apache 2.0 license allows commercial use.

**Q: How accurate is the documentation?**
A: Very accurate for code structure (100%). Business logic understanding depends on AI quality.

**Q: Can I deploy this to production?**
A: Yes! Add authentication, use proper secrets management, and scale with Kubernetes.

**Q: What if my language isn't supported?**
A: Create a plugin! It's just one Kotlin class. See "Create a Plugin" above.

## 🌟 Star Us!

If you find this useful, please ⭐ star this repo on GitHub!

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourrepo/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourrepo/discussions)
- **Email**: support@example.com

---

**Made with ❤️ using Kotlin, Spring Boot, and AI**

🚀 Happy Documenting!
