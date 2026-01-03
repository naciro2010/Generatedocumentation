# Documentation Generator

> Simple Python script that auto-generates README.md for legacy code repositories using AI

## What It Does

This tool analyzes your code repository (Java, PHP, Spring Boot, etc.) and automatically generates documentation by:

1. **Detecting** the framework (Struts, PHP, Spring Boot)
2. **Extracting** code structure (endpoints, database queries, security issues)
3. **Enriching** with AI analysis (business logic, features, recommendations)
4. **Generating** a complete README.md file

## Quick Start

### Prerequisites

- Python 3.11+
- Anthropic API key ([get one here](https://console.anthropic.com/))

### Installation

```bash
# Clone this repo
git clone <your-repo>
cd doc-generator

# Install dependencies
pip install -r requirements.txt

# Set your API key
export ANTHROPIC_API_KEY="your-api-key-here"
```

### Usage

```bash
# Generate README for current directory
python generate_doc.py

# Generate for a specific repository
python generate_doc.py --repo /path/to/your/legacy/code

# Custom output file
python generate_doc.py --repo /path/to/code --output DOCUMENTATION.md
```

## GitLab CI Integration

Add this to your `.gitlab-ci.yml`:

```yaml
generate-docs:
  image: python:3.11-slim
  script:
    - pip install -r requirements.txt
    - python generate_doc.py --repo . --output README.md
  variables:
    ANTHROPIC_API_KEY: $ANTHROPIC_API_KEY
```

Set `ANTHROPIC_API_KEY` in **Settings → CI/CD → Variables**

## Supported Technologies

| Framework | Detection | Extraction |
|-----------|-----------|------------|
| **Struts 1.x/2.x** | ✅ struts-config.xml, Action classes | ✅ Endpoints from XML mappings |
| **PHP Legacy** | ✅ .php files, mysql_query patterns | ✅ Pages, SQL queries, security issues |
| **Spring Boot** | ✅ @RestController, application.yml | ✅ REST endpoints from annotations |

## How It Works

### 1. Framework Detection

The script scans your repository for specific files and patterns:

```python
# Example: Struts detection
if "struts-config.xml" exists → Struts (score: 0.5)
if "extends Action" found → Struts (score: +0.3)
```

### 2. Code Extraction

Extracts structure using simple regex patterns:

```python
# Example: Find Spring endpoints
@GetMapping("/users") → GET /users endpoint
@PostMapping("/login") → POST /login endpoint
```

### 3. AI Enrichment

Sends extracted structure to Claude AI:

```
Input:  Found endpoint POST /login in LoginAction.java
Output: This endpoint handles user authentication.
        Business rule: Validates username/password against database.
        Security risk: SQL injection detected (line 23)
```

### 4. README Generation

Combines everything into a readable README.md with:
- Project overview
- Main features
- API endpoints table
- Security concerns
- Technical stack

## Example Output

```markdown
# Project Documentation

## Overview
This is a Struts 1.x application for user management...

## Main Features
- User authentication
- Profile management
- Admin dashboard

## API Endpoints
| Method | Path | Handler |
|--------|------|---------|
| POST | /login | LoginAction |
| GET | /users | UserListAction |

## Security Concerns
⚠️ SQL Injection risk found in LoginAction.java:23
```

## Configuration

### Environment Variables

- `ANTHROPIC_API_KEY` - Your Anthropic API key (required)

### Command Line Options

```
--repo PATH       Path to repository (default: current directory)
--output FILE     Output file path (default: README.md)
--api-key KEY     API key (alternative to env var)
```

## Project Structure

```
.
├── generate_doc.py      # Main script (~400 lines)
├── requirements.txt     # Python dependencies
├── .gitlab-ci.yml      # CI/CD pipeline example
└── README.md           # This file
```

## Code Structure

The script is organized into simple classes:

```python
FrameworkDetector   # Detects Struts/PHP/Spring
CodeExtractor       # Extracts endpoints/queries
LLMEnricher        # Calls Claude AI
ReadmeGenerator    # Generates final README
```

Each class has clear responsibilities and is easy to extend.

## Limitations

- **Basic extraction**: Uses regex, not full AST parsing
- **File limit**: Processes first 100 files per framework
- **LLM cost**: Each run costs ~$0.01-0.05 (varies by repo size)
- **No deep analysis**: Won't understand complex business logic

## Troubleshooting

### "No ANTHROPIC_API_KEY found"

Set the environment variable:
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

### "Error calling LLM"

The script will generate basic documentation without AI enrichment.

### "No framework detected"

The script supports Struts, PHP, and Spring Boot. For other frameworks, it will generate minimal documentation.

## Extending

### Add a New Framework

1. Add detection logic in `FrameworkDetector`:
```python
@staticmethod
def _detect_django(repo_path: Path) -> float:
    if list(repo_path.rglob("manage.py")):
        return 0.8
    return 0.0
```

2. Add extraction logic in `CodeExtractor`:
```python
@staticmethod
def _extract_django(repo_path: Path) -> ProjectStructure:
    # Extract Django views, models, etc.
    pass
```

3. Register in the `detect()` and `extract()` methods

## License

Apache 2.0

## Credits

Built with:
- [Anthropic Claude](https://anthropic.com) - AI analysis
- [Jinja2](https://jinja.palletsprojects.com/) - Template engine
- Python 3.11+
