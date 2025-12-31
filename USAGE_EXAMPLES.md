# Usage Examples - Simplified Architecture

## Table des Matières
1. [Quick Start](#quick-start)
2. [API Examples](#api-examples)
3. [Multi-Framework Analysis](#multi-framework-analysis)
4. [LLM Validation](#llm-validation)
5. [File System Inspection](#file-system-inspection)
6. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Setup (< 1 minute)

```bash
# 1. Clone and build
git clone <your-repo>
cd universal-doc-generator

# 2. Build
./gradlew build

# 3. Run API server
./gradlew :apps:api:bootRun

# API is now running on http://localhost:8080
```

### Verify Installation

```bash
# Health check
curl http://localhost:8080/actuator/health

# Response:
# {"status":"UP"}
```

---

## API Examples

### 1. Import a Project

```bash
# Import from GitHub
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-express-app",
    "gitUrl": "https://github.com/user/my-express-app"
  }'

# Response:
# {
#   "projectId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
#   "status": "PENDING"
# }
```

**Other options:**

```bash
# Import from ZIP
curl -X POST http://localhost:8080/v1/projects/import \
  -d '{
    "name": "my-project",
    "zipUrl": "https://example.com/project.zip"
  }'

# Import from local path
curl -X POST http://localhost:8080/v1/projects/import \
  -d '{
    "name": "my-project",
    "localPath": "/path/to/project"
  }'
```

### 2. Check Project Status

```bash
PROJECT_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"

# Get project details
curl http://localhost:8080/v1/projects/$PROJECT_ID

# Response:
# {
#   "id": "f47ac10b-...",
#   "name": "my-express-app",
#   "status": "PENDING",
#   "createdAt": 1704067200000,
#   "updatedAt": 1704067200000,
#   "linesOfCode": 0,
#   "fileCount": 0
# }
```

### 3. Analyze Project (Multi-Framework)

```bash
# NEW: Analyze with all applicable plugins
curl -X POST http://localhost:8080/v1/projects/$PROJECT_ID/analyze

# Response:
# {
#   "projectId": "f47ac10b-...",
#   "status": "SUCCESS",
#   "appliedPlugins": [
#     "spring-boot",
#     "express-js"
#   ],
#   "moduleCount": 15,
#   "endpointCount": 42,
#   "warnings": [],
#   "message": "Analysis complete with 2 plugins"
# }
```

**This is NEW and powerful!**
- Multiple plugins run in parallel
- Results merged automatically
- Detects Express + Java backends, or Spring Boot + React, etc.

### 4. Generate Documentation

```bash
curl -X POST http://localhost:8080/v1/projects/$PROJECT_ID/generate-docs

# Response:
# {
#   "jobId": "job-123456",
#   "status": "PENDING"
# }
```

### 5. List All Projects

```bash
curl http://localhost:8080/v1/projects

# Response:
# [
#   {
#     "id": "f47ac10b-...",
#     "name": "my-express-app",
#     "status": "ANALYZED",
#     "createdAt": 1704067200000
#   },
#   {
#     "id": "a1b2c3d4-...",
#     "name": "my-spring-app",
#     "status": "COMPLETED",
#     "createdAt": 1704067300000
#   }
# ]
```

### 6. Filter by Status

```bash
# Get only analyzed projects
curl "http://localhost:8080/v1/projects?status=ANALYZED"

# Get only failed projects
curl "http://localhost:8080/v1/projects?status=FAILED"

# Possible statuses: PENDING, IMPORTING, IMPORTED, ANALYZING, ANALYZED, GENERATING_DOCS, COMPLETED, FAILED
```

---

## Multi-Framework Analysis

### Example 1: Express.js + React Frontend

**Project structure:**
```
my-app/
├── backend/          # Express API
│   ├── app.js
│   ├── routes/
│   │   ├── users.js
│   │   └── products.js
│   └── package.json
└── frontend/         # React
    ├── src/
    ├── App.js
    └── package.json
```

**Before (old architecture):**
- Only Express backend detected
- React frontend missed

**After (multi-framework):**
```bash
curl -X POST http://localhost:8080/v1/projects/$PROJECT_ID/analyze

# Response shows BOTH:
# {
#   "appliedPlugins": [
#     "express-js",      # ← Detected backend
#     "react"            # ← NEW: Detected frontend!
#   ],
#   "endpointCount": 12,  # From Express
#   "componentCount": 45, # From React
#   ...
# }
```

---

### Example 2: Java Monolith + Python Microservice

**Project structure:**
```
mono-repo/
├── java-app/
│   ├── src/main/java/
│   └── pom.xml        # Maven → Spring Boot
└── python-service/
    ├── src/
    └── requirements.txt  # FastAPI
```

**Response:**
```json
{
  "appliedPlugins": [
    "spring-boot",     # ← Detected Java
    "fastapi"         # ← Detected Python
  ],
  "moduleCount": 28,
  "endpointCount": 67,
  "warnings": [
    "Detected frameworks from multiple languages - ensure separate deployment"
  ]
}
```

---

### Example 3: Legacy PHP + Modern Node.js

**Project structure:**
```
legacy-modernized/
├── old-php/          # Old code
│   ├── index.php
│   └── .htaccess
└── new-api/          # New API
    ├── app.js
    └── package.json
```

**Response:**
```json
{
  "appliedPlugins": [
    "php-legacy",    # ← Detected old PHP
    "express-js"     # ← Detected Node.js
  ],
  "warnings": [
    "Multiple frameworks detected",
    "Migration in progress? Consider single framework"
  ]
}
```

---

## LLM Validation

### Without LLM (Default - Free)

```bash
# 1. Analyze (with multi-framework)
curl -X POST http://localhost:8080/v1/projects/$PROJECT_ID/analyze

# Response shows what plugins found
# ✅ Fast, free, no LLM calls
```

### With LLM Validation (Optional - $0.05-0.10)

**Step 1: Enable LLM in configuration**

```yaml
# application.yml
docgen:
  llm:
    enabled: true
    provider: anthropic  # or openai, gemini, bedrock
    api-key: sk-ant-...
    model: claude-3-5-sonnet-20241022
```

**Step 2: Run analysis**

```bash
curl -X POST http://localhost:8080/v1/projects/$PROJECT_ID/analyze
```

**Worker now:**
1. Runs plugins (fast)
2. Sends to LLM: "Did I miss any endpoints?"
3. Merges LLM findings
4. Returns complete picture

**Example LLM response:**
```json
{
  "projectId": "f47ac10b-...",
  "status": "SUCCESS",
  "appliedPlugins": ["express-js"],
  "endpointCount": 12,
  "llmValidation": {
    "confidence": 0.95,
    "foundMissingEndpoints": [
      {
        "method": "POST",
        "path": "/api/webhooks",
        "reason": "Dynamic route registration via middleware"
      }
    ]
  }
}
```

---

## File System Inspection

### View Generated Files

```bash
# Project storage
tree ./storage/

# Output:
# storage/
# ├── projects/
# │   ├── f47ac10b-58cc-4372-a567-0e02b2c3d479.json
# │   ├── a1b2c3d4-1234-5678-9012-abcdef123456.json
# │   └── ... more projects
# └── jobs/
#     ├── job-123456.json
#     ├── job-234567.json
#     └── ... more jobs
```

### Inspect Project JSON

```bash
# Read raw project data
cat ./storage/projects/f47ac10b-58cc-4372-a567-0e02b2c3d479.json | jq .

# Output:
# {
#   "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
#   "name": "my-express-app",
#   "status": "ANALYZED",
#   "createdAt": 1704067200000,
#   "projectIRJson": "{ ...full IR object... }",
#   "fingerprintJson": "{ ...languages, frameworks... }",
#   ...
# }
```

### Pretty-print ProjectIR

```bash
# Extract and format ProjectIR
cat ./storage/projects/$PROJECT_ID.json | jq '.projectIRJson | fromjson | .' | head -50

# Output shows:
# {
#   "modules": [
#     {
#       "id": "module-1",
#       "name": "api",
#       "endpoints": [
#         {
#           "method": "GET",
#           "path": "/users",
#           "handler": "UserController.getUsers"
#         }
#       ]
#     }
#   ]
# }
```

### Check Job Status

```bash
# List all jobs
ls -la ./storage/jobs/

# Check specific job
cat ./storage/jobs/job-123456.json | jq .

# Output:
# {
#   "id": "job-123456",
#   "projectId": "f47ac10b-...",
#   "status": "COMPLETED",
#   "createdAt": 1704067200000,
#   "completedAt": 1704067500000,
#   "outputPath": "./storage/f47ac10b-58cc-4372-a567-0e02b2c3d479/docs"
# }
```

---

## Troubleshooting

### Issue 1: "Project not found" after import

```bash
# ❌ Problem
curl -X POST http://localhost:8080/v1/projects/import -d '{"name":"test"}'
# Returns: {"projectId":"abc-123","status":"PENDING"}

curl http://localhost:8080/v1/projects/abc-123
# Returns: 404 Not Found!

# ✅ Solution: Project needs time to import
# Wait 2-3 seconds, then try again

sleep 3
curl http://localhost:8080/v1/projects/abc-123
# Now returns the project!
```

### Issue 2: "Storage path permission denied"

```bash
# Error: Permission denied: ./storage/projects
# Cause: Directory doesn't exist or wrong permissions

# Solution:
mkdir -p ./storage/projects ./storage/jobs
chmod 755 ./storage

# Try again
./gradlew :apps:api:bootRun
```

### Issue 3: "No applicable plugins found"

```bash
# Error: "No applicable plugins found for project. Scores: {spring-boot: 0.2, express-js: 0.1}"
# Cause: Project language/tech not recognized

# Check fingerprint
cat ./storage/projects/$PROJECT_ID.json | jq '.fingerprintJson | fromjson'

# If languages is empty:
# - Might not have recognized file types
# - Try with a well-known project structure
```

### Issue 4: "Out of memory"

```bash
# Error: java.lang.OutOfMemoryError: Java heap space
# Cause: Project too large

# Solution: Increase heap in settings
export GRADLE_OPTS="-Xmx2g"
./gradlew :apps:api:bootRun

# Or: Limit project size in config
# docgen.max-project-size: 500000000  # 500MB
```

### Issue 5: "LLM API key not working"

```bash
# Verify config
grep -A 5 "docgen.llm" application.yml

# Set env var correctly
export LLM_API_KEY="sk-ant-..."

# Restart and check logs
./gradlew :apps:api:bootRun 2>&1 | grep -i llm
```

---

## Complete Workflow Example

### Scenario: Analyze a real project (Express.js + React)

```bash
#!/bin/bash

PROJECT_ID=""
STORAGE="./storage"

# 1. Start the server
echo "🚀 Starting API server..."
./gradlew :apps:api:bootRun &
API_PID=$!
sleep 3

# 2. Import project
echo "📥 Importing project..."
RESPONSE=$(curl -s -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mern-stack-app",
    "gitUrl": "https://github.com/example/mern-app"
  }')

PROJECT_ID=$(echo $RESPONSE | jq -r '.projectId')
echo "✅ Project ID: $PROJECT_ID"

# 3. Wait for import
echo "⏳ Waiting for import..."
sleep 5

# 4. Check status
echo "📊 Checking status..."
curl -s http://localhost:8080/v1/projects/$PROJECT_ID | jq '.status'

# 5. Analyze (multi-framework)
echo "🔬 Analyzing with multiple frameworks..."
ANALYSIS=$(curl -s -X POST http://localhost:8080/v1/projects/$PROJECT_ID/analyze)
echo $ANALYSIS | jq '.'

# 6. Display results
echo "📈 Results:"
echo "   Plugins used: $(echo $ANALYSIS | jq -r '.appliedPlugins[]')"
echo "   Endpoints: $(echo $ANALYSIS | jq '.endpointCount')"
echo "   Modules: $(echo $ANALYSIS | jq '.moduleCount')"

# 7. Inspect storage
echo "💾 Storage:"
ls -lh "$STORAGE/projects/$PROJECT_ID.json"

# 8. View project data
echo "📄 Project data:"
cat "$STORAGE/projects/$PROJECT_ID.json" | jq '.name, .status, .linesOfCode'

echo "✅ Workflow complete!"

# Cleanup
kill $API_PID
```

**Run it:**
```bash
chmod +x workflow.sh
./workflow.sh
```

**Output:**
```
🚀 Starting API server...
📥 Importing project...
✅ Project ID: f47ac10b-58cc-4372-a567-0e02b2c3d479
⏳ Waiting for import...
📊 Checking status...
"IMPORTED"
🔬 Analyzing with multiple frameworks...
{
  "projectId": "f47ac10b-...",
  "status": "SUCCESS",
  "appliedPlugins": [
    "express-js",
    "react"
  ],
  "endpointCount": 18,
  "moduleCount": 12
}
📈 Results:
   Plugins used: express-js
   Plugins used: react
   Endpoints: 18
   Modules: 12
💾 Storage:
-rw-r--r--  1 user  staff  2.4K  Dec 30 10:15 f47ac10b-58cc-4372-a567-0e02b2c3d479.json
📄 Project data:
"mern-stack-app"
"ANALYZED"
15000
✅ Workflow complete!
```

---

## Summary

The new simplified architecture:
- ✅ No database setup needed
- ✅ File-based storage (Git-versionable)
- ✅ Multi-framework analysis
- ✅ Optional LLM validation (cheap)
- ✅ Fast and simple

Ready to use! 🚀
