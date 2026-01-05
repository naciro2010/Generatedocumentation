#!/usr/bin/env python3
"""
Simple GitLab Documentation Generator
Extracts code facts using tree-sitter and generates documentation.
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Any, Optional, Set

# Try to import tree-sitter
try:
    from tree_sitter_language_pack import get_language, get_parser
    TREE_SITTER_AVAILABLE = True
except ImportError:
    TREE_SITTER_AVAILABLE = False
    print("Warning: tree-sitter-language-pack not available. Install with: pip install tree-sitter-language-pack", file=sys.stderr)


# ============================================================================
# CONSTANTS
# ============================================================================

SUPPORTED_LANGUAGES = {
    '.py': 'python',
    '.ts': 'typescript',
    '.tsx': 'tsx',
    '.js': 'javascript',
    '.jsx': 'javascript',
}

MANIFEST_FILES = [
    'package.json',
    'pyproject.toml',
    'requirements.txt',
    'setup.py',
    'Dockerfile',
    'docker-compose.yml',
    'Cargo.toml',
    'go.mod',
    'pom.xml',
    'build.gradle',
    'build.gradle.kts',
]

MIGRATIONS_PATTERNS = [
    '**/migrations/**/*.sql',
    '**/alembic/versions/**/*.py',
    '**/migrate/**/*.sql',
    '**/db/migrate/**/*.sql',
]

IGNORE_DIRS = {
    'node_modules', '.git', '__pycache__', 'venv', 'env',
    '.venv', 'dist', 'build', 'target', '.idea', '.vscode',
    'coverage', '.pytest_cache', '.mypy_cache', 'eggs',
}


# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def get_all_files(repo_path: str, max_files: int = 400) -> List[Path]:
    """Recursively get all files in repo, respecting ignore dirs."""
    all_files = []
    repo = Path(repo_path)

    for root, dirs, files in os.walk(repo):
        # Remove ignored directories from traversal
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]

        for file in files:
            file_path = Path(root) / file
            all_files.append(file_path)

            if len(all_files) >= max_files:
                print(f"Warning: Reached max files limit ({max_files})", file=sys.stderr)
                return all_files

    return all_files


def get_file_extension(file_path: Path) -> str:
    """Get file extension."""
    return file_path.suffix.lower()


def read_file_safe(file_path: Path, max_size_mb: int = 5) -> Optional[str]:
    """Safely read a file with size limit."""
    try:
        if file_path.stat().st_size > max_size_mb * 1024 * 1024:
            return None

        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            return f.read()
    except Exception as e:
        print(f"Error reading {file_path}: {e}", file=sys.stderr)
        return None


def get_relative_path(file_path: Path, repo_path: Path) -> str:
    """Get relative path from repo root."""
    try:
        return str(file_path.relative_to(repo_path))
    except ValueError:
        return str(file_path)


# ============================================================================
# TECH OVERVIEW EXTRACTION
# ============================================================================

def extract_tech_overview(files: List[Path], repo_path: Path) -> Dict[str, Any]:
    """Extract technology overview from repository."""
    languages = defaultdict(int)
    manifests = []

    for file_path in files:
        ext = get_file_extension(file_path)
        if ext in SUPPORTED_LANGUAGES:
            languages[SUPPORTED_LANGUAGES[ext]] += 1

        if file_path.name in MANIFEST_FILES:
            manifests.append(get_relative_path(file_path, repo_path))

    return {
        'languages': dict(languages),
        'manifests': manifests,
    }


def extract_repo_structure(repo_path: Path) -> Dict[str, Any]:
    """Extract repository structure."""
    structure = {
        'top_level_dirs': [],
        'key_files': [],
    }

    # Get top-level directories
    for item in repo_path.iterdir():
        if item.is_dir() and item.name not in IGNORE_DIRS:
            structure['top_level_dirs'].append(item.name)

    # Get key files in root
    for item in repo_path.iterdir():
        if item.is_file() and (item.name in MANIFEST_FILES or item.name.lower().startswith('readme')):
            structure['key_files'].append(item.name)

    return structure


# ============================================================================
# TREE-SITTER CODE PARSING
# ============================================================================

def parse_python_symbols(content: str, file_path: str, repo_path: Path) -> Dict[str, List[Dict]]:
    """Parse Python file for classes, functions, and FastAPI routes."""
    if not TREE_SITTER_AVAILABLE:
        return {'classes': [], 'functions': [], 'routes': []}

    try:
        language = get_language('python')
        parser = get_parser('python')
        tree = parser.parse(bytes(content, 'utf8'))

        symbols = {
            'classes': [],
            'functions': [],
            'routes': [],
        }

        def traverse(node, parent_class=None):
            # Extract classes
            if node.type == 'class_definition':
                class_name_node = node.child_by_field_name('name')
                if class_name_node:
                    class_name = content[class_name_node.start_byte:class_name_node.end_byte]
                    symbols['classes'].append({
                        'name': class_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    })
                    # Recurse into class body
                    for child in node.children:
                        traverse(child, class_name)

            # Extract functions
            elif node.type == 'function_definition':
                func_name_node = node.child_by_field_name('name')
                if func_name_node:
                    func_name = content[func_name_node.start_byte:func_name_node.end_byte]

                    # Check for decorators (FastAPI routes)
                    decorators = []
                    if node.parent and node.parent.type == 'decorated_definition':
                        for child in node.parent.children:
                            if child.type == 'decorator':
                                dec_text = content[child.start_byte:child.end_byte]
                                decorators.append(dec_text)

                    symbol_info = {
                        'name': func_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    }

                    if parent_class:
                        symbol_info['class'] = parent_class

                    symbols['functions'].append(symbol_info)

                    # Detect FastAPI routes
                    for dec in decorators:
                        route_match = re.search(r'@(\w+)\.(get|post|put|delete|patch)\s*\(\s*["\']([^"\']+)["\']', dec)
                        if route_match:
                            app_name, method, path = route_match.groups()
                            symbols['routes'].append({
                                'method': method.upper(),
                                'path': path,
                                'handler': func_name,
                                'file': get_relative_path(Path(file_path), repo_path),
                                'line': node.start_point[0] + 1,
                            })

            # Recurse
            for child in node.children:
                traverse(child, parent_class)

        traverse(tree.root_node)
        return symbols

    except Exception as e:
        print(f"Error parsing Python file {file_path}: {e}", file=sys.stderr)
        return {'classes': [], 'functions': [], 'routes': []}


def parse_typescript_symbols(content: str, file_path: str, repo_path: Path, lang: str) -> Dict[str, List[Dict]]:
    """Parse TypeScript/JavaScript file for exports and Express routes."""
    if not TREE_SITTER_AVAILABLE:
        return {'functions': [], 'classes': [], 'routes': []}

    try:
        language = get_language(lang)
        parser = get_parser(lang)
        tree = parser.parse(bytes(content, 'utf8'))

        symbols = {
            'functions': [],
            'classes': [],
            'routes': [],
        }

        def traverse(node):
            # Export declarations
            if node.type == 'export_statement':
                for child in node.children:
                    if child.type == 'function_declaration':
                        name_node = child.child_by_field_name('name')
                        if name_node:
                            func_name = content[name_node.start_byte:name_node.end_byte]
                            symbols['functions'].append({
                                'name': func_name,
                                'file': get_relative_path(Path(file_path), repo_path),
                                'line': child.start_point[0] + 1,
                                'exported': True,
                            })
                    elif child.type == 'class_declaration':
                        name_node = child.child_by_field_name('name')
                        if name_node:
                            class_name = content[name_node.start_byte:name_node.end_byte]
                            symbols['classes'].append({
                                'name': class_name,
                                'file': get_relative_path(Path(file_path), repo_path),
                                'line': child.start_point[0] + 1,
                                'exported': True,
                            })

            # Express route detection (router.get/post/put/delete)
            elif node.type == 'call_expression':
                callee = node.child_by_field_name('function')
                if callee and callee.type == 'member_expression':
                    callee_text = content[callee.start_byte:callee.end_byte]
                    route_match = re.match(r'(\w+)\.(get|post|put|delete|patch)', callee_text)
                    if route_match:
                        router_name, method = route_match.groups()
                        # Try to extract path from first argument
                        args = node.child_by_field_name('arguments')
                        if args:
                            for child in args.children:
                                if child.type == 'string':
                                    path = content[child.start_byte:child.end_byte].strip('"\'')
                                    symbols['routes'].append({
                                        'method': method.upper(),
                                        'path': path,
                                        'router': router_name,
                                        'file': get_relative_path(Path(file_path), repo_path),
                                        'line': node.start_point[0] + 1,
                                    })
                                    break

            # Recurse
            for child in node.children:
                traverse(child)

        traverse(tree.root_node)
        return symbols

    except Exception as e:
        print(f"Error parsing {lang} file {file_path}: {e}", file=sys.stderr)
        return {'functions': [], 'classes': [], 'routes': []}


def extract_symbols(files: List[Path], repo_path: Path) -> Dict[str, Any]:
    """Extract symbols from all supported files."""
    all_symbols = {
        'classes': [],
        'functions': [],
        'routes': [],
    }

    for file_path in files:
        ext = get_file_extension(file_path)
        if ext not in SUPPORTED_LANGUAGES:
            continue

        content = read_file_safe(file_path)
        if not content:
            continue

        lang = SUPPORTED_LANGUAGES[ext]

        if lang == 'python':
            symbols = parse_python_symbols(content, str(file_path), repo_path)
        elif lang in ['typescript', 'javascript', 'tsx']:
            symbols = parse_typescript_symbols(content, str(file_path), repo_path, lang)
        else:
            continue

        all_symbols['classes'].extend(symbols.get('classes', []))
        all_symbols['functions'].extend(symbols.get('functions', []))
        all_symbols['routes'].extend(symbols.get('routes', []))

    return all_symbols


# ============================================================================
# DATABASE EXTRACTION
# ============================================================================

def extract_database_info(files: List[Path], repo_path: Path) -> Dict[str, Any]:
    """Extract database information from migrations and models."""
    db_info = {
        'tables': [],
        'migrations': [],
        'models': [],
        'foreign_keys': [],
    }

    for file_path in files:
        rel_path = get_relative_path(file_path, repo_path)

        # Check if it's a migration file
        is_migration = any(
            'migration' in rel_path.lower() or
            'alembic' in rel_path.lower() or
            '/db/' in rel_path
            for _ in [1]
        )

        if not is_migration and file_path.suffix.lower() not in ['.sql', '.py']:
            continue

        content = read_file_safe(file_path)
        if not content:
            continue

        # Extract SQL CREATE TABLE statements
        if file_path.suffix.lower() == '.sql' or is_migration:
            # Find CREATE TABLE
            create_tables = re.finditer(
                r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"\']?(\w+)[`"\']?',
                content,
                re.IGNORECASE
            )
            for match in create_tables:
                table_name = match.group(1)
                if table_name not in [t['name'] for t in db_info['tables']]:
                    db_info['tables'].append({
                        'name': table_name,
                        'source': rel_path,
                        'type': 'sql',
                    })

            # Find FOREIGN KEY relationships
            fk_pattern = r'FOREIGN\s+KEY\s*\([`"\']?(\w+)[`"\']?\)\s*REFERENCES\s+[`"\']?(\w+)[`"\']?'
            foreign_keys = re.finditer(fk_pattern, content, re.IGNORECASE)
            for match in foreign_keys:
                column, ref_table = match.groups()
                db_info['foreign_keys'].append({
                    'from_table': table_name if 'table_name' in locals() else 'unknown',
                    'to_table': ref_table,
                    'column': column,
                })

        # Extract SQLAlchemy models
        if file_path.suffix.lower() == '.py':
            # Look for __tablename__
            tablename_matches = re.finditer(
                r'__tablename__\s*=\s*["\'](\w+)["\']',
                content
            )
            for match in tablename_matches:
                table_name = match.group(1)

                # Try to find the class name
                lines = content[:match.start()].split('\n')
                class_name = None
                for line in reversed(lines):
                    class_match = re.search(r'class\s+(\w+)', line)
                    if class_match:
                        class_name = class_match.group(1)
                        break

                if table_name not in [t['name'] for t in db_info['tables']]:
                    db_info['tables'].append({
                        'name': table_name,
                        'source': rel_path,
                        'type': 'model',
                        'class': class_name,
                    })

                if class_name:
                    db_info['models'].append({
                        'class': class_name,
                        'table': table_name,
                        'file': rel_path,
                    })

        # Track migration files
        if is_migration:
            db_info['migrations'].append(rel_path)

    return db_info


# ============================================================================
# FUNCTIONAL RULES EXTRACTION
# ============================================================================

def extract_functional_rules(files: List[Path], repo_path: Path) -> List[Dict[str, Any]]:
    """Extract functional rules from code."""
    rules = []
    rule_id = 1

    for file_path in files:
        ext = get_file_extension(file_path)
        if ext not in SUPPORTED_LANGUAGES:
            continue

        content = read_file_safe(file_path)
        if not content:
            continue

        rel_path = get_relative_path(file_path, repo_path)
        lines = content.split('\n')

        lang = SUPPORTED_LANGUAGES[ext]

        # Pattern matching for rules
        for i, line in enumerate(lines):
            line_num = i + 1
            stripped = line.strip()

            # Python patterns
            if lang == 'python':
                # raise statements
                if 'raise ' in stripped and not stripped.startswith('#'):
                    # Extract error message
                    error_match = re.search(r'raise\s+\w+\(["\']([^"\']+)["\']', stripped)
                    if error_match:
                        rule_text = error_match.group(1)
                    else:
                        rule_text = stripped

                    # Get context (few lines)
                    start_line = max(0, i - 2)
                    end_line = min(len(lines), i + 3)
                    snippet = '\n'.join(lines[start_line:end_line])

                    rules.append({
                        'id': f'FR-{rule_id:03d}',
                        'text': rule_text,
                        'type': 'validation',
                        'evidence': [{
                            'file': rel_path,
                            'line': line_num,
                            'snippet': snippet[:500],  # Limit snippet size
                        }]
                    })
                    rule_id += 1

                # assert statements
                elif 'assert ' in stripped and not stripped.startswith('#'):
                    start_line = max(0, i - 2)
                    end_line = min(len(lines), i + 3)
                    snippet = '\n'.join(lines[start_line:end_line])

                    rules.append({
                        'id': f'FR-{rule_id:03d}',
                        'text': stripped,
                        'type': 'assertion',
                        'evidence': [{
                            'file': rel_path,
                            'line': line_num,
                            'snippet': snippet[:500],
                        }]
                    })
                    rule_id += 1

            # TypeScript/JavaScript patterns
            elif lang in ['typescript', 'javascript', 'tsx']:
                # throw statements
                if 'throw new' in stripped or 'throw Error' in stripped:
                    error_match = re.search(r'throw\s+(?:new\s+)?(?:\w+)?Error\(["\']([^"\']+)["\']', stripped)
                    if error_match:
                        rule_text = error_match.group(1)
                    else:
                        rule_text = stripped

                    start_line = max(0, i - 2)
                    end_line = min(len(lines), i + 3)
                    snippet = '\n'.join(lines[start_line:end_line])

                    rules.append({
                        'id': f'FR-{rule_id:03d}',
                        'text': rule_text,
                        'type': 'validation',
                        'evidence': [{
                            'file': rel_path,
                            'line': line_num,
                            'snippet': snippet[:500],
                        }]
                    })
                    rule_id += 1

            # Test evidence (all languages)
            if '/test' in rel_path.lower() or 'test_' in file_path.name:
                test_keywords = ['should', 'must', 'cannot', 'reject', 'expect']
                if any(keyword in stripped.lower() for keyword in test_keywords):
                    # This is test evidence, can be associated with rules
                    # For simplicity, we'll create a rule entry
                    if len(stripped) > 20 and not stripped.startswith('#') and not stripped.startswith('//'):
                        start_line = max(0, i - 1)
                        end_line = min(len(lines), i + 2)
                        snippet = '\n'.join(lines[start_line:end_line])

                        rules.append({
                            'id': f'FR-{rule_id:03d}',
                            'text': stripped[:100],
                            'type': 'test_evidence',
                            'evidence': [{
                                'file': rel_path,
                                'line': line_num,
                                'snippet': snippet[:500],
                            }]
                        })
                        rule_id += 1

    # Limit rules to avoid overwhelming output
    return rules[:50]  # Keep top 50 rules


# ============================================================================
# DOC CONTEXT GENERATION
# ============================================================================

def generate_doc_context(repo_path: Path, max_files: int = 400) -> Dict[str, Any]:
    """Generate the doc_context.json with all extracted facts."""
    print("Scanning repository...")
    files = get_all_files(str(repo_path), max_files)
    print(f"Found {len(files)} files")

    print("Extracting tech overview...")
    tech = extract_tech_overview(files, repo_path)

    print("Extracting repository structure...")
    structure = extract_repo_structure(repo_path)

    print("Extracting symbols...")
    symbols = extract_symbols(files, repo_path)

    print("Extracting database information...")
    database = extract_database_info(files, repo_path)

    print("Extracting functional rules...")
    rules = extract_functional_rules(files, repo_path)

    context = {
        'repository': {
            'path': str(repo_path),
            'name': repo_path.name,
        },
        'tech_overview': tech,
        'structure': structure,
        'symbols': symbols,
        'database': database,
        'functional_rules': rules,
        'stats': {
            'total_files': len(files),
            'total_classes': len(symbols['classes']),
            'total_functions': len(symbols['functions']),
            'total_routes': len(symbols['routes']),
            'total_tables': len(database['tables']),
            'total_rules': len(rules),
        }
    }

    return context


# ============================================================================
# MARKDOWN GENERATION
# ============================================================================

def generate_readme(context: Dict[str, Any]) -> str:
    """Generate README.md content."""
    repo_name = context['repository']['name']
    tech = context['tech_overview']
    stats = context['stats']

    # Detect main language
    languages = tech.get('languages', {})
    main_lang = max(languages.items(), key=lambda x: x[1])[0] if languages else 'Unknown'

    # Detect how to run
    manifests = tech.get('manifests', [])
    run_instructions = []

    if any('package.json' in m for m in manifests):
        run_instructions.append("```bash\nnpm install\nnpm start\n```")
    if any('requirements.txt' in m or 'pyproject.toml' in m for m in manifests):
        run_instructions.append("```bash\npip install -r requirements.txt\npython main.py  # or your entry point\n```")
    if any('Dockerfile' in m for m in manifests):
        run_instructions.append("```bash\ndocker build -t {} .\ndocker run {}\n```".format(repo_name, repo_name))

    readme = f"""# {repo_name}

## Overview

This repository is primarily written in **{main_lang}** and contains:
- {stats['total_classes']} classes
- {stats['total_functions']} functions
- {stats['total_routes']} API routes
- {stats['total_tables']} database tables

## Documentation

- [Architecture](docs/architecture.md) - System architecture and component diagrams
- [Database](docs/database.md) - Database schema and relationships
- [Functional Rules](docs/functional_rules.md) - Business rules and validations

## Technology Stack

"""

    if languages:
        readme += "**Languages:**\n"
        for lang, count in sorted(languages.items(), key=lambda x: x[1], reverse=True):
            readme += f"- {lang.capitalize()}: {count} files\n"
        readme += "\n"

    if manifests:
        readme += "**Configuration Files:**\n"
        for manifest in manifests:
            readme += f"- `{manifest}`\n"
        readme += "\n"

    if run_instructions:
        readme += "## Quick Start\n\n"
        readme += "\n".join(run_instructions)
        readme += "\n"

    readme += """
## Project Structure

"""

    dirs = context['structure'].get('top_level_dirs', [])
    if dirs:
        for d in sorted(dirs)[:10]:  # Show top 10 dirs
            readme += f"- `{d}/`\n"

    readme += """

---
*This documentation was automatically generated.*
"""

    return readme


def generate_architecture_md(context: Dict[str, Any]) -> str:
    """Generate docs/architecture.md with Mermaid diagrams."""
    structure = context['structure']
    routes = context['symbols']['routes']
    dirs = structure.get('top_level_dirs', [])

    # Build a simple component diagram based on directory structure
    components = []
    if any('frontend' in d.lower() or 'client' in d.lower() or 'ui' in d.lower() for d in dirs):
        components.append('Frontend')
    if any('api' in d.lower() or 'server' in d.lower() or 'backend' in d.lower() for d in dirs):
        components.append('API')
    if any('service' in d.lower() or 'domain' in d.lower() or 'core' in d.lower() for d in dirs):
        components.append('Services')
    if any('db' in d.lower() or 'database' in d.lower() or 'model' in d.lower() for d in dirs):
        components.append('Database')

    # If no components detected, use generic structure
    if not components:
        components = [d.capitalize() for d in dirs[:6]]  # Show up to 6 dirs

    doc = """# Architecture Documentation

## System Overview

This document describes the high-level architecture of the system.

## Component Diagram

```mermaid
graph TB
"""

    # Build component connections
    if len(components) >= 2:
        for i in range(len(components) - 1):
            doc += f"    {components[i]} --> {components[i+1]}\n"
    elif components:
        doc += f"    {components[0]}\n"

    doc += "```\n\n"

    # Add directory structure
    doc += "## Directory Structure\n\n"
    for d in sorted(dirs):
        doc += f"- **{d}/** - "
        # Add simple description based on name
        if 'test' in d.lower():
            doc += "Test files"
        elif 'doc' in d.lower():
            doc += "Documentation"
        elif 'src' in d.lower() or 'lib' in d.lower():
            doc += "Source code"
        elif 'api' in d.lower():
            doc += "API implementation"
        elif 'db' in d.lower() or 'model' in d.lower():
            doc += "Database and models"
        elif 'config' in d.lower():
            doc += "Configuration files"
        else:
            doc += "Application code"
        doc += "\n"

    doc += "\n"

    # Add API sequence diagram if routes detected
    if routes:
        doc += "## API Flow Example\n\n"
        doc += "Example API request flow:\n\n"
        doc += "```mermaid\nsequenceDiagram\n"
        doc += "    participant Client\n"
        doc += "    participant API\n"
        doc += "    participant Service\n"
        doc += "    participant Database\n\n"

        # Use first route as example
        route = routes[0]
        doc += f"    Client->>API: {route['method']} {route['path']}\n"
        doc += f"    API->>Service: {route.get('handler', 'process_request')}()\n"
        doc += "    Service->>Database: query()\n"
        doc += "    Database-->>Service: results\n"
        doc += "    Service-->>API: response\n"
        doc += "    API-->>Client: JSON response\n"
        doc += "```\n\n"

    # List all API routes
    if routes:
        doc += "## API Endpoints\n\n"
        doc += "| Method | Path | Handler | File |\n"
        doc += "|--------|------|---------|------|\n"

        for route in sorted(routes, key=lambda x: (x.get('path', ''), x.get('method', ''))):
            method = route.get('method', 'N/A')
            path = route.get('path', 'N/A')
            handler = route.get('handler', route.get('router', 'N/A'))
            file_path = route.get('file', 'N/A')
            line = route.get('line', '')

            file_ref = f"{file_path}:{line}" if line else file_path
            doc += f"| {method} | `{path}` | {handler} | {file_ref} |\n"

        doc += "\n"

    doc += "---\n*Auto-generated from code analysis*\n"

    return doc


def generate_database_md(context: Dict[str, Any]) -> str:
    """Generate docs/database.md with ER diagram."""
    db_info = context['database']
    tables = db_info.get('tables', [])
    foreign_keys = db_info.get('foreign_keys', [])
    models = db_info.get('models', [])

    doc = """# Database Documentation

## Schema Overview

"""

    if tables:
        doc += f"The database contains **{len(tables)} tables**.\n\n"

        # Generate Mermaid ER diagram
        doc += "## Entity-Relationship Diagram\n\n"
        doc += "```mermaid\nerDiagram\n"

        # Add tables
        for table in tables:
            table_name = table['name']
            # For now, just declare tables
            doc += f"    {table_name} {{\n"
            doc += f"        int id PK\n"
            doc += "    }\n"

        # Add relationships if foreign keys detected
        if foreign_keys:
            for fk in foreign_keys:
                from_table = fk.get('from_table', 'unknown')
                to_table = fk.get('to_table', 'unknown')
                if from_table != 'unknown' and to_table != 'unknown':
                    doc += f"    {from_table} ||--o{{ {to_table} : references\n"

        doc += "```\n\n"

        # List tables
        doc += "## Tables\n\n"
        for table in tables:
            name = table['name']
            source = table.get('source', 'N/A')
            table_type = table.get('type', 'unknown')

            doc += f"### {name}\n\n"
            doc += f"- **Source:** `{source}`\n"
            doc += f"- **Type:** {table_type}\n"

            if table.get('class'):
                doc += f"- **Model Class:** `{table['class']}`\n"

            doc += "\n"
    else:
        doc += "No database tables detected.\n\n"

    # List models
    if models:
        doc += "## ORM Models\n\n"
        doc += "| Class | Table | File |\n"
        doc += "|-------|-------|------|\n"

        for model in models:
            doc += f"| {model['class']} | {model['table']} | {model['file']} |\n"

        doc += "\n"

    # List migrations
    migrations = db_info.get('migrations', [])
    if migrations:
        doc += "## Migrations\n\n"
        for migration in migrations[:20]:  # Show first 20
            doc += f"- `{migration}`\n"
        doc += "\n"

    doc += "---\n*Auto-generated from code analysis*\n"

    return doc


def generate_functional_rules_md(context: Dict[str, Any]) -> str:
    """Generate docs/functional_rules.md."""
    rules = context.get('functional_rules', [])

    doc = """# Functional Rules Documentation

## Business Rules and Validations

This document lists the functional rules extracted from the codebase.

"""

    if rules:
        # Group rules by type
        by_type = defaultdict(list)
        for rule in rules:
            by_type[rule.get('type', 'other')].append(rule)

        for rule_type, type_rules in by_type.items():
            doc += f"## {rule_type.replace('_', ' ').title()}\n\n"

            for rule in type_rules:
                rule_id = rule.get('id', 'FR-???')
                text = rule.get('text', 'No description')
                evidence = rule.get('evidence', [])

                doc += f"### {rule_id}: {text}\n\n"

                if evidence:
                    doc += "**Evidence:**\n\n"
                    for ev in evidence[:3]:  # Show max 3 evidence items
                        file_path = ev.get('file', 'N/A')
                        line = ev.get('line', 'N/A')
                        snippet = ev.get('snippet', '')

                        doc += f"- `{file_path}:{line}`\n\n"

                        if snippet:
                            # Limit snippet to 8 lines
                            snippet_lines = snippet.split('\n')[:8]
                            doc += "```python\n" if '.py' in file_path else "```javascript\n"
                            doc += '\n'.join(snippet_lines)
                            doc += "\n```\n\n"

                doc += "\n"
    else:
        doc += "No functional rules detected.\n\n"

    doc += "---\n*Auto-generated from code analysis*\n"

    return doc


# ============================================================================
# LLM ENHANCEMENT (OPTIONAL)
# ============================================================================

def enhance_with_llm(context: Dict[str, Any], provider: str) -> Dict[str, str]:
    """Optionally enhance documentation with LLM."""
    if provider == 'none':
        return {}

    print(f"LLM enhancement with {provider} is not implemented in this version.")
    print("Generating template-based documentation...")
    return {}


# ============================================================================
# MAIN FUNCTION
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description='Generate documentation for a GitLab repository'
    )
    parser.add_argument(
        '--repo',
        type=str,
        default='.',
        help='Path to repository (default: current directory)'
    )
    parser.add_argument(
        '--out',
        type=str,
        default='.',
        help='Output directory (default: current directory)'
    )
    parser.add_argument(
        '--llm-provider',
        type=str,
        choices=['none', 'openai', 'anthropic', 'ollama'],
        default='none',
        help='LLM provider for enhancement (default: none)'
    )
    parser.add_argument(
        '--max-files',
        type=int,
        default=400,
        help='Maximum number of files to process (default: 400)'
    )

    args = parser.parse_args()

    # Validate inputs
    repo_path = Path(args.repo).resolve()
    if not repo_path.exists():
        print(f"Error: Repository path does not exist: {repo_path}", file=sys.stderr)
        sys.exit(1)

    out_path = Path(args.out).resolve()
    out_path.mkdir(parents=True, exist_ok=True)

    # Check tree-sitter availability
    if not TREE_SITTER_AVAILABLE:
        print("Warning: Running without tree-sitter. Symbol extraction will be limited.", file=sys.stderr)

    # Generate doc context
    print(f"Analyzing repository: {repo_path}")
    context = generate_doc_context(repo_path, args.max_files)

    # Save doc_context.json
    context_file = out_path / 'doc_context.json'
    print(f"Writing {context_file}")
    with open(context_file, 'w', encoding='utf-8') as f:
        json.dump(context, f, indent=2)

    # Enhance with LLM (optional)
    enhance_with_llm(context, args.llm_provider)

    # Generate documentation files
    print("Generating documentation...")

    # Create docs directory
    docs_dir = out_path / 'docs'
    docs_dir.mkdir(exist_ok=True)

    # Generate README.md
    readme_file = out_path / 'README.md'
    print(f"Writing {readme_file}")
    with open(readme_file, 'w', encoding='utf-8') as f:
        f.write(generate_readme(context))

    # Generate architecture.md
    arch_file = docs_dir / 'architecture.md'
    print(f"Writing {arch_file}")
    with open(arch_file, 'w', encoding='utf-8') as f:
        f.write(generate_architecture_md(context))

    # Generate database.md
    db_file = docs_dir / 'database.md'
    print(f"Writing {db_file}")
    with open(db_file, 'w', encoding='utf-8') as f:
        f.write(generate_database_md(context))

    # Generate functional_rules.md
    rules_file = docs_dir / 'functional_rules.md'
    print(f"Writing {rules_file}")
    with open(rules_file, 'w', encoding='utf-8') as f:
        f.write(generate_functional_rules_md(context))

    print("\n✅ Documentation generation complete!")
    print(f"\nGenerated files:")
    print(f"  - {readme_file}")
    print(f"  - {arch_file}")
    print(f"  - {db_file}")
    print(f"  - {rules_file}")
    print(f"  - {context_file}")

    print(f"\nStats:")
    print(f"  - Files analyzed: {context['stats']['total_files']}")
    print(f"  - Classes found: {context['stats']['total_classes']}")
    print(f"  - Functions found: {context['stats']['total_functions']}")
    print(f"  - API routes found: {context['stats']['total_routes']}")
    print(f"  - Database tables found: {context['stats']['total_tables']}")
    print(f"  - Functional rules found: {context['stats']['total_rules']}")


if __name__ == '__main__':
    main()
