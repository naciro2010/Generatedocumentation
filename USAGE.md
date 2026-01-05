# Documentation Generator - Usage Guide

## Overview

`generate_docs.py` is a simple, pipeline-friendly documentation generator that extracts facts from your codebase using tree-sitter and generates comprehensive documentation.

## Features

- **Deterministic**: Extracts facts from code, doesn't invent content
- **Safe**: Never sends raw source code to LLMs (only structured facts)
- **Simple**: Single Python script with minimal dependencies
- **CI-Friendly**: No interactive prompts, designed for automation
- **Multi-Language**: Supports Python and TypeScript/JavaScript

## Installation

```bash
# Install dependencies
pip install -r requirements.txt
```

## Basic Usage

### Generate Documentation

```bash
# Generate docs for current directory
python generate_docs.py --repo . --out . --llm-provider none

# Generate docs for specific repository
python generate_docs.py --repo /path/to/repo --out /path/to/output --llm-provider none

# Limit number of files processed
python generate_docs.py --repo . --out . --max-files 200
```

### CLI Arguments

- `--repo <path>`: Path to repository (default: current directory)
- `--out <path>`: Output directory (default: current directory)
- `--llm-provider <none|openai|anthropic|ollama>`: LLM provider for enhancement (default: none)
- `--max-files <number>`: Maximum files to process (default: 400)

## Output Files

The generator creates the following files:

1. **README.md** - Project overview with quick start guide
2. **docs/architecture.md** - System architecture with Mermaid diagrams
3. **docs/database.md** - Database schema with ER diagrams
4. **docs/functional_rules.md** - Business rules and validations
5. **doc_context.json** - Structured facts extracted from code

## What Gets Extracted

### 1. Tech Overview
- Programming languages detected
- Configuration files (package.json, requirements.txt, etc.)
- Repository structure

### 2. Code Symbols
- **Python**: Classes, functions, FastAPI routes (@app.get/post/put/delete)
- **TypeScript/JavaScript**: Exported functions/classes, Express routes

### 3. API Routes
- **FastAPI**: Decorators like `@app.get("/path")`
- **Express**: Calls like `router.get("/path", ...)`

### 4. Database Information
- Migration files
- CREATE TABLE statements in SQL
- SQLAlchemy models (via `__tablename__`)
- Foreign key relationships

### 5. Functional Rules
- Python: `raise` and `assert` statements
- TypeScript/JavaScript: `throw` statements
- Test evidence with keywords: should, must, cannot, reject

## CI/CD Integration

### GitLab CI

Create `.gitlab-ci.yml`:

```yaml
stages:
  - documentation

generate-docs:
  stage: documentation
  image: python:3.11-slim
  script:
    # Install dependencies
    - pip install -r requirements.txt

    # Generate documentation
    - python generate_docs.py --repo . --out . --llm-provider none

    # Commit and push (optional)
    - git config user.name "GitLab CI"
    - git config user.email "ci@gitlab.com"
    - git add README.md docs/ doc_context.json
    - git diff --staged --quiet || git commit -m "docs: Update auto-generated documentation [skip ci]"
    - git push origin HEAD:${CI_COMMIT_REF_NAME}

  only:
    - main
    - master

  artifacts:
    paths:
      - README.md
      - docs/
      - doc_context.json
    expire_in: 30 days
```

### GitHub Actions

Create `.github/workflows/docs.yml`:

```yaml
name: Generate Documentation

on:
  push:
    branches: [main, master]
  workflow_dispatch:

jobs:
  generate-docs:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Install dependencies
        run: |
          pip install -r requirements.txt

      - name: Generate documentation
        run: |
          python generate_docs.py --repo . --out . --llm-provider none

      - name: Commit and push
        run: |
          git config user.name "GitHub Actions"
          git config user.email "actions@github.com"
          git add README.md docs/ doc_context.json
          git diff --staged --quiet || git commit -m "docs: Update auto-generated documentation [skip ci]"
          git push
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Jenkins

Create `Jenkinsfile`:

```groovy
pipeline {
    agent any

    stages {
        stage('Generate Documentation') {
            steps {
                script {
                    sh '''
                        pip install -r requirements.txt
                        python generate_docs.py --repo . --out . --llm-provider none
                    '''
                }
            }
        }

        stage('Publish') {
            steps {
                archiveArtifacts artifacts: 'README.md,docs/**,doc_context.json', fingerprint: true

                // Optional: commit and push
                sh '''
                    git config user.name "Jenkins"
                    git config user.email "jenkins@example.com"
                    git add README.md docs/ doc_context.json
                    git diff --staged --quiet || git commit -m "docs: Update auto-generated documentation [skip ci]"
                    git push origin HEAD:${BRANCH_NAME}
                '''
            }
        }
    }
}
```

## LLM Enhancement (Optional)

To enable LLM-based documentation enhancement:

1. Uncomment the desired LLM provider in `requirements.txt`
2. Install the provider: `pip install anthropic` or `pip install openai`
3. Set environment variables:
   - For Anthropic: `export ANTHROPIC_API_KEY=your_key`
   - For OpenAI: `export OPENAI_API_KEY=your_key`
4. Run with provider: `python generate_docs.py --llm-provider anthropic`

**Note**: LLM providers only improve wording and organization. They do NOT add new facts or invent endpoints/tables.

## Customization

### Supported File Types

Currently supports:
- Python (`.py`)
- TypeScript (`.ts`, `.tsx`)
- JavaScript (`.js`, `.jsx`)

### Ignored Directories

The following directories are automatically ignored:
- `node_modules`
- `.git`
- `__pycache__`
- `venv`, `env`, `.venv`
- `dist`, `build`, `target`
- `.idea`, `.vscode`
- `coverage`, `.pytest_cache`, `.mypy_cache`

### File Size Limits

- Maximum file size: 5 MB
- Files larger than this are skipped

## Troubleshooting

### Tree-sitter not available

If you see the warning "tree-sitter-language-pack not available":

```bash
pip install tree-sitter-language-pack
```

### No symbols extracted

- Ensure your code files are in supported languages (Python, TypeScript, JavaScript)
- Check that files are not in ignored directories
- Verify files are not too large (> 5 MB)

### Empty documentation

If documentation is empty or minimal:
- Check that you're running from the correct repository directory
- Increase `--max-files` limit if needed
- Verify your code files have the correct extensions

## Examples

### Example 1: FastAPI Project

```bash
# Generate docs for a FastAPI project
python generate_docs.py --repo /path/to/fastapi-project --out /path/to/fastapi-project

# Output will include:
# - Detected FastAPI routes (@app.get, @app.post, etc.)
# - SQLAlchemy models and tables
# - Validation rules from raise statements
```

### Example 2: Express.js Project

```bash
# Generate docs for an Express project
python generate_docs.py --repo /path/to/express-project --out /path/to/express-project

# Output will include:
# - Express routes (router.get, router.post, etc.)
# - Exported functions and classes
# - Error handling patterns (throw statements)
```

### Example 3: Large Repository

```bash
# Limit processing for large repos
python generate_docs.py --repo . --out . --max-files 1000
```

## Contributing

The generator is designed to be simple and extensible. To add support for new languages:

1. Add the language to `SUPPORTED_LANGUAGES` dictionary
2. Create a parsing function (e.g., `parse_golang_symbols`)
3. Add the parser to `extract_symbols` function

## Safety & Privacy

- **No raw code sent to LLMs**: Only structured facts (doc_context.json) are sent
- **Deterministic output**: Documentation is generated from extracted facts, not invented
- **Local processing**: All code analysis happens locally using tree-sitter
- **Optional LLM**: LLM enhancement is completely optional and disabled by default

## License

See repository license file.
