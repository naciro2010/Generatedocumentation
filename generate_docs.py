#!/usr/bin/env python3
"""
Simple GitLab Documentation Generator
Extracts code facts using tree-sitter and generates documentation.
"""

import argparse
import hashlib
import json
import os
import re
import sys
import time
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, Any, Optional, Set
from dataclasses import dataclass, asdict

# Try to import tree-sitter
try:
    from tree_sitter_language_pack import get_language, get_parser
    TREE_SITTER_AVAILABLE = True
except ImportError:
    TREE_SITTER_AVAILABLE = False
    print("Warning: tree-sitter-language-pack not available. Install with: pip install tree-sitter-language-pack", file=sys.stderr)

# LLM Imports
try:
    from anthropic import Anthropic
    HAS_ANTHROPIC = True
except ImportError:
    HAS_ANTHROPIC = False

try:
    from openai import OpenAI
    HAS_OPENAI = True
except ImportError:
    HAS_OPENAI = False

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False


# ============================================================================
# CONSTANTS
# ============================================================================

SUPPORTED_LANGUAGES = {
    '.py': 'python',
    '.ts': 'typescript',
    '.tsx': 'tsx',
    '.js': 'javascript',
    '.jsx': 'javascript',
    '.java': 'java',
    '.kt': 'kotlin',
    '.kts': 'kotlin',
    '.php': 'php',
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

CACHE_DIR = Path.home() / '.cache' / 'doc_generator'
CACHE_EXPIRY_HOURS = 24


# ============================================================================
# CACHE SYSTEM
# ============================================================================

class CacheManager:
    """Manages caching of parsing results for large repositories."""

    def __init__(self, cache_dir: Path = CACHE_DIR, enabled: bool = True):
        self.cache_dir = cache_dir
        self.enabled = enabled
        if self.enabled:
            self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _get_file_hash(self, file_path: Path) -> str:
        """Generate hash from file path and modification time."""
        try:
            mtime = file_path.stat().st_mtime
            content = f"{file_path}:{mtime}"
            return hashlib.md5(content.encode()).hexdigest()
        except Exception:
            return hashlib.md5(str(file_path).encode()).hexdigest()

    def _get_cache_path(self, file_hash: str) -> Path:
        """Get cache file path for a given hash."""
        return self.cache_dir / f"{file_hash}.json"

    def get(self, file_path: Path) -> Optional[Dict[str, Any]]:
        """Get cached parsing result for a file."""
        if not self.enabled:
            return None

        try:
            file_hash = self._get_file_hash(file_path)
            cache_path = self._get_cache_path(file_hash)

            if not cache_path.exists():
                return None

            # Check if cache is expired
            cache_age = time.time() - cache_path.stat().st_mtime
            if cache_age > CACHE_EXPIRY_HOURS * 3600:
                cache_path.unlink()
                return None

            with open(cache_path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception as e:
            print(f"Cache read error for {file_path}: {e}", file=sys.stderr)
            return None

    def set(self, file_path: Path, data: Dict[str, Any]):
        """Cache parsing result for a file."""
        if not self.enabled:
            return

        try:
            file_hash = self._get_file_hash(file_path)
            cache_path = self._get_cache_path(file_hash)

            with open(cache_path, 'w', encoding='utf-8') as f:
                json.dump(data, f)
        except Exception as e:
            print(f"Cache write error for {file_path}: {e}", file=sys.stderr)

    def clear(self):
        """Clear all cached data."""
        if not self.enabled or not self.cache_dir.exists():
            return

        try:
            for cache_file in self.cache_dir.glob("*.json"):
                cache_file.unlink()
            print(f"Cache cleared: {self.cache_dir}")
        except Exception as e:
            print(f"Cache clear error: {e}", file=sys.stderr)

    def get_stats(self) -> Dict[str, Any]:
        """Get cache statistics."""
        if not self.enabled or not self.cache_dir.exists():
            return {'enabled': False}

        cache_files = list(self.cache_dir.glob("*.json"))
        total_size = sum(f.stat().st_size for f in cache_files)

        return {
            'enabled': True,
            'location': str(self.cache_dir),
            'files': len(cache_files),
            'size_mb': round(total_size / (1024 * 1024), 2),
        }


# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def get_all_files(repo_path: str, max_files: int = 400,
                  include_pattern: Optional[str] = None,
                  exclude_pattern: Optional[str] = None) -> List[Path]:
    """Recursively get all files in repo, respecting ignore dirs and regex filters."""
    all_files = []
    repo = Path(repo_path)

    # Compile regex patterns if provided
    include_regex = re.compile(include_pattern) if include_pattern else None
    exclude_regex = re.compile(exclude_pattern) if exclude_pattern else None

    for root, dirs, files in os.walk(repo):
        # Remove ignored directories from traversal
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]

        for file in files:
            file_path = Path(root) / file
            relative_path = str(file_path.relative_to(repo))

            # Apply include filter
            if include_regex and not include_regex.search(relative_path):
                continue

            # Apply exclude filter
            if exclude_regex and exclude_regex.search(relative_path):
                continue

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


def parse_java_symbols(content: str, file_path: str, repo_path: Path) -> Dict[str, List[Dict]]:
    """Parse Java file for classes, methods, and Spring annotations."""
    if not TREE_SITTER_AVAILABLE:
        return {'classes': [], 'functions': [], 'routes': []}

    try:
        language = get_language('java')
        parser = get_parser('java')
        tree = parser.parse(bytes(content, 'utf8'))

        symbols = {
            'classes': [],
            'functions': [],
            'routes': [],
        }

        def traverse(node, parent_class=None):
            # Extract classes
            if node.type == 'class_declaration':
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

            # Extract methods
            elif node.type == 'method_declaration':
                method_name_node = node.child_by_field_name('name')
                if method_name_node:
                    method_name = content[method_name_node.start_byte:method_name_node.end_byte]

                    # Check for Spring annotations
                    annotations = []
                    if node.parent:
                        for sibling in node.parent.children:
                            if sibling.type == 'marker_annotation' or sibling.type == 'annotation':
                                ann_text = content[sibling.start_byte:sibling.end_byte]
                                annotations.append(ann_text)

                    symbol_info = {
                        'name': method_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    }

                    if parent_class:
                        symbol_info['class'] = parent_class

                    symbols['functions'].append(symbol_info)

                    # Detect Spring REST annotations
                    for ann in annotations:
                        route_match = re.search(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*\(\s*(?:value\s*=\s*)?["\']([^"\']+)["\']', ann)
                        if route_match:
                            annotation_type, path = route_match.groups()
                            method = annotation_type.replace('Mapping', '').upper() if 'Mapping' in annotation_type else 'GET'
                            symbols['routes'].append({
                                'method': method,
                                'path': path,
                                'handler': method_name,
                                'file': get_relative_path(Path(file_path), repo_path),
                                'line': node.start_point[0] + 1,
                            })

            # Recurse
            for child in node.children:
                traverse(child, parent_class)

        traverse(tree.root_node)
        return symbols

    except Exception as e:
        print(f"Error parsing Java file {file_path}: {e}", file=sys.stderr)
        return {'classes': [], 'functions': [], 'routes': []}


def parse_kotlin_symbols(content: str, file_path: str, repo_path: Path) -> Dict[str, List[Dict]]:
    """Parse Kotlin file for classes, functions, and Spring/Ktor annotations."""
    if not TREE_SITTER_AVAILABLE:
        return {'classes': [], 'functions': [], 'routes': []}

    try:
        language = get_language('kotlin')
        parser = get_parser('kotlin')
        tree = parser.parse(bytes(content, 'utf8'))

        symbols = {
            'classes': [],
            'functions': [],
            'routes': [],
        }

        def traverse(node, parent_class=None):
            # Extract classes
            if node.type == 'class_declaration':
                name_node = node.child_by_field_name('name')
                if name_node:
                    class_name = content[name_node.start_byte:name_node.end_byte]
                    symbols['classes'].append({
                        'name': class_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    })
                    for child in node.children:
                        traverse(child, class_name)

            # Extract functions
            elif node.type == 'function_declaration':
                name_node = node.child_by_field_name('simple_identifier')
                if name_node:
                    func_name = content[name_node.start_byte:name_node.end_byte]

                    # Check for annotations
                    annotations = []
                    if node.parent:
                        for sibling in node.parent.children:
                            if sibling.type == 'annotation':
                                ann_text = content[sibling.start_byte:sibling.end_byte]
                                annotations.append(ann_text)

                    symbol_info = {
                        'name': func_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    }

                    if parent_class:
                        symbol_info['class'] = parent_class

                    symbols['functions'].append(symbol_info)

                    # Detect Spring/Ktor routes
                    for ann in annotations:
                        # Spring annotations
                        route_match = re.search(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(\s*["\']([^"\']+)["\']', ann)
                        if route_match:
                            annotation_type, path = route_match.groups()
                            method = annotation_type.replace('Mapping', '').upper()
                            symbols['routes'].append({
                                'method': method,
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
        print(f"Error parsing Kotlin file {file_path}: {e}", file=sys.stderr)
        return {'classes': [], 'functions': [], 'routes': []}


def parse_php_symbols(content: str, file_path: str, repo_path: Path) -> Dict[str, List[Dict]]:
    """Parse PHP file for classes, functions, and routes."""
    if not TREE_SITTER_AVAILABLE:
        return {'classes': [], 'functions': [], 'routes': []}

    try:
        language = get_language('php')
        parser = get_parser('php')
        tree = parser.parse(bytes(content, 'utf8'))

        symbols = {
            'classes': [],
            'functions': [],
            'routes': [],
        }

        def traverse(node, parent_class=None):
            # Extract classes
            if node.type == 'class_declaration':
                name_node = node.child_by_field_name('name')
                if name_node:
                    class_name = content[name_node.start_byte:name_node.end_byte]
                    symbols['classes'].append({
                        'name': class_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    })
                    for child in node.children:
                        traverse(child, class_name)

            # Extract functions/methods
            elif node.type in ['function_definition', 'method_declaration']:
                name_node = node.child_by_field_name('name')
                if name_node:
                    func_name = content[name_node.start_byte:name_node.end_byte]

                    symbol_info = {
                        'name': func_name,
                        'file': get_relative_path(Path(file_path), repo_path),
                        'line': node.start_point[0] + 1,
                    }

                    if parent_class:
                        symbol_info['class'] = parent_class

                    symbols['functions'].append(symbol_info)

            # Recurse
            for child in node.children:
                traverse(child, parent_class)

        traverse(tree.root_node)
        return symbols

    except Exception as e:
        print(f"Error parsing PHP file {file_path}: {e}", file=sys.stderr)
        return {'classes': [], 'functions': [], 'routes': []}


def extract_symbols(files: List[Path], repo_path: Path, cache: Optional[CacheManager] = None) -> Dict[str, Any]:
    """Extract symbols from all supported files with optional caching."""
    all_symbols = {
        'classes': [],
        'functions': [],
        'routes': [],
    }

    cache_hits = 0
    cache_misses = 0

    for file_path in files:
        ext = get_file_extension(file_path)
        if ext not in SUPPORTED_LANGUAGES:
            continue

        # Try to get from cache first
        if cache:
            cached_symbols = cache.get(file_path)
            if cached_symbols:
                cache_hits += 1
                all_symbols['classes'].extend(cached_symbols.get('classes', []))
                all_symbols['functions'].extend(cached_symbols.get('functions', []))
                all_symbols['routes'].extend(cached_symbols.get('routes', []))
                continue
            cache_misses += 1

        content = read_file_safe(file_path)
        if not content:
            continue

        lang = SUPPORTED_LANGUAGES[ext]

        if lang == 'python':
            symbols = parse_python_symbols(content, str(file_path), repo_path)
        elif lang in ['typescript', 'javascript', 'tsx']:
            symbols = parse_typescript_symbols(content, str(file_path), repo_path, lang)
        elif lang == 'java':
            symbols = parse_java_symbols(content, str(file_path), repo_path)
        elif lang == 'kotlin':
            symbols = parse_kotlin_symbols(content, str(file_path), repo_path)
        elif lang == 'php':
            symbols = parse_php_symbols(content, str(file_path), repo_path)
        else:
            continue

        # Cache the result
        if cache:
            cache.set(file_path, symbols)

        all_symbols['classes'].extend(symbols.get('classes', []))
        all_symbols['functions'].extend(symbols.get('functions', []))
        all_symbols['routes'].extend(symbols.get('routes', []))

    if cache and (cache_hits > 0 or cache_misses > 0):
        print(f"Cache: {cache_hits} hits, {cache_misses} misses ({cache_hits/(cache_hits+cache_misses)*100:.1f}% hit rate)")

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

def generate_doc_context(repo_path: Path, max_files: int = 400, use_cache: bool = True,
                        include_pattern: Optional[str] = None,
                        exclude_pattern: Optional[str] = None) -> Dict[str, Any]:
    """Generate the doc_context.json with all extracted facts."""
    # Initialize cache
    cache = CacheManager(enabled=use_cache) if use_cache else None

    if cache and use_cache:
        cache_stats = cache.get_stats()
        if cache_stats.get('enabled'):
            print(f"Cache enabled: {cache_stats['files']} cached files ({cache_stats['size_mb']} MB)")

    print("Scanning repository...")
    if include_pattern:
        print(f"Include pattern: {include_pattern}")
    if exclude_pattern:
        print(f"Exclude pattern: {exclude_pattern}")

    files = get_all_files(str(repo_path), max_files, include_pattern, exclude_pattern)
    print(f"Found {len(files)} files")

    print("Extracting tech overview...")
    tech = extract_tech_overview(files, repo_path)

    print("Extracting repository structure...")
    structure = extract_repo_structure(repo_path)

    print("Extracting symbols...")
    symbols = extract_symbols(files, repo_path, cache)

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

def generate_project_overview(context: Dict[str, Any]) -> str:
    """Generate PROJECT_OVERVIEW.md content."""
    # Check if we have LLM enhancement
    llm_content = context.get('llm_enhanced', {}).get('project_overview')
    if llm_content:
        # If it looks like full markdown, return it
        if '# ' in llm_content:
            return llm_content
        return f"# Project Overview\n\n{llm_content}\n\n---\n*Enhanced by AI analysis*\n"

    repo_name = context['repository']['name']
    tech = context['tech_overview']
    stats = context['stats']
    structure = context['structure']
    symbols = context['symbols']

    # Detect main language
    languages = tech.get('languages', {})
    main_lang = max(languages.items(), key=lambda x: x[1])[0] if languages else 'Unknown'

    doc = f"""# {repo_name} - Project Overview

## Project Summary

**Name:** {repo_name}
**Primary Language:** {main_lang}
**Type:** {_infer_project_type(tech, symbols, context.get('database', {}))}

## Codebase Statistics

| Metric | Count |
|--------|-------|
| Total Files | {stats['total_files']} |
| Classes | {stats['total_classes']} |
| Functions/Methods | {stats['total_functions']} |
| API Routes | {stats['total_routes']} |
| Database Tables | {stats['total_tables']} |
| Business Rules | {stats['total_rules']} |

## Technology Stack

### Languages
"""

    if languages:
        doc += "\n| Language | Files |\n|----------|-------|\n"
        for lang, count in sorted(languages.items(), key=lambda x: x[1], reverse=True):
            doc += f"| {lang.capitalize()} | {count} |\n"
        doc += "\n"

    manifests = tech.get('manifests', [])
    if manifests:
        doc += "### Build & Configuration Files\n\n"
        for manifest in manifests:
            doc += f"- `{manifest}`\n"
        doc += "\n"

    doc += """### Detected Technologies

"""

    # Detect technologies
    if any('package.json' in m for m in manifests):
        doc += "- **Node.js** - JavaScript/TypeScript runtime\n"
        doc += "- **npm/yarn** - Package management\n"
    if any('requirements.txt' in m or 'pyproject.toml' in m for m in manifests):
        doc += "- **Python** - Core language\n"
        doc += "- **pip** - Package management\n"
    if any('build.gradle' in m or 'pom.xml' in m for m in manifests):
        doc += "- **Java/JVM** - Runtime platform\n"
        doc += "- **Gradle/Maven** - Build automation\n"
    if any('Dockerfile' in m for m in manifests):
        doc += "- **Docker** - Containerization\n"
    if any('docker-compose' in m for m in manifests):
        doc += "- **Docker Compose** - Multi-container orchestration\n"

    if stats['total_tables'] > 0:
        doc += "- **SQL Database** - Data persistence\n"

    doc += "\n## Module Structure\n\n"

    dirs = structure.get('top_level_dirs', [])
    if dirs:
        doc += "| Directory | Purpose |\n|-----------|----------|\n"
        for d in sorted(dirs):
            purpose = _infer_directory_purpose(d)
            doc += f"| `{d}/` | {purpose} |\n"
        doc += "\n"

    # Key Components
    classes = symbols.get('classes', [])
    if classes:
        doc += f"## Key Classes ({len(classes)} total)\n\n"
        doc += "| Class | Location |\n|-------|----------|\n"
        for cls in classes[:20]:  # Top 20
            doc += f"| `{cls['name']}` | {cls['file']}:{cls.get('line', '')} |\n"
        if len(classes) > 20:
            doc += f"\n*...and {len(classes) - 20} more classes*\n"
        doc += "\n"

    # API Routes
    routes = symbols.get('routes', [])
    if routes:
        doc += f"## API Endpoints ({len(routes)} total)\n\n"
        doc += "| Method | Path | Handler |\n|--------|------|----------|\n"
        for route in sorted(routes, key=lambda x: (x.get('path', ''), x.get('method', '')))[:30]:
            method = route.get('method', 'N/A')
            path = route.get('path', 'N/A')
            handler = route.get('handler', route.get('router', 'N/A'))
            doc += f"| {method} | `{path}` | {handler} |\n"
        if len(routes) > 30:
            doc += f"\n*...and {len(routes) - 30} more endpoints*\n"
        doc += "\n"

    doc += """## Related Documentation

- [Architecture](docs/architecture.md) - System architecture and design
- [Database](docs/database.md) - Database schema and data models
- [Functional Rules](docs/functional_rules.md) - Business logic and validation rules

---
*Generated by automated code analysis*
"""

    return doc


def _infer_project_type(tech, symbols, database):
    """Infer project type from characteristics."""
    routes = symbols.get('routes', [])
    tables = database.get('tables', [])

    if len(routes) > 0:
        if len(tables) > 0:
            return "Web API with Database (Backend Application)"
        return "Web API / REST Service"
    elif len(tables) > 0:
        return "Database Application"
    elif 'python' in tech.get('languages', {}):
        return "Python Application"
    elif 'javascript' in tech.get('languages', {}) or 'typescript' in tech.get('languages', {}):
        return "JavaScript/TypeScript Application"
    elif 'kotlin' in tech.get('languages', {}) or 'java' in tech.get('languages', {}):
        return "JVM Application"
    else:
        return "Software Application"


def _infer_directory_purpose(dirname):
    """Infer directory purpose from name."""
    dirname_lower = dirname.lower()

    purposes = {
        'src': 'Source code',
        'lib': 'Library code',
        'app': 'Application code',
        'test': 'Test files',
        'tests': 'Test files',
        'doc': 'Documentation',
        'docs': 'Documentation',
        'build': 'Build artifacts',
        'dist': 'Distribution files',
        'config': 'Configuration files',
        'scripts': 'Utility scripts',
        'migrations': 'Database migrations',
        'api': 'API endpoints',
        'controllers': 'HTTP controllers',
        'models': 'Data models',
        'views': 'View templates',
        'services': 'Business logic services',
        'utils': 'Utility functions',
        'helpers': 'Helper functions',
        'middleware': 'Middleware components',
        'routes': 'Route definitions',
        'public': 'Public assets',
        'static': 'Static files',
        'assets': 'Application assets',
        'resources': 'Application resources',
        'gradle': 'Gradle build system',
        'maven': 'Maven build system',
        '.git': 'Git version control',
        '.github': 'GitHub configuration',
        'node_modules': 'Node.js dependencies',
        'vendor': 'Third-party dependencies',
    }

    for key, purpose in purposes.items():
        if key in dirname_lower:
            return purpose

    return 'Application module'


def generate_architecture_md(context: Dict[str, Any]) -> str:
    """Generate docs/architecture.md with Mermaid diagrams."""
    # Check if we have LLM enhancement
    llm_content = context.get('llm_enhanced', {}).get('architecture_enhancement')
    if llm_content:
        # If it looks like full markdown, return it
        if '# ' in llm_content:
            return llm_content
        return f"# Architecture Guide\n\n{llm_content}\n\n---\n*Enhanced by AI analysis*\n"

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
# LLM ENHANCEMENT (AI)
# ============================================================================

@dataclass
class LLMConfig:
    provider: str  # 'anthropic', 'openai', 'ollama'
    api_key: Optional[str] = None
    model: Optional[str] = None
    base_url: Optional[str] = None


class LLMClient:
    """Unified LLM client supporting Anthropic, OpenAI, and Ollama"""

    def __init__(self, config: LLMConfig):
        self.config = config
        self.client = None
        self.model = config.model

        if config.provider == 'anthropic':
            if not HAS_ANTHROPIC:
                raise ImportError("Package 'anthropic' not found.")
            api_key = config.api_key or os.getenv('ANTHROPIC_API_KEY')
            if not api_key:
                raise ValueError("ANTHROPIC_API_KEY not set!")
            self.client = Anthropic(api_key=api_key)
            if not self.model:
                self.model = "claude-haiku-4-5-20251001"

        elif config.provider == 'openai':
            if not HAS_OPENAI:
                raise ImportError("Package 'openai' not found.")
            api_key = config.api_key or os.getenv('OPENAI_API_KEY')
            if not api_key:
                raise ValueError("OPENAI_API_KEY not set!")
            self.client = OpenAI(api_key=api_key)
            if not self.model:
                self.model = "gpt-4o"

        elif config.provider == 'ollama':
            if not HAS_REQUESTS:
                raise ImportError("Package 'requests' not found.")
            self.base_url = config.base_url or "http://localhost:11434"
            if not self.model:
                self.model = "llama3.2"
            # Test Ollama connection
            try:
                requests.get(f"{self.base_url}/api/tags", timeout=2)
            except Exception:
                raise ValueError(f"Cannot connect to Ollama at {self.base_url}")

    def analyze(self, prompt: str, max_tokens: int = 4000) -> str:
        """Send prompt to LLM and get response"""
        if self.config.provider == 'anthropic':
            response = self.client.messages.create(
                model=self.model,
                max_tokens=max_tokens,
                messages=[{"role": "user", "content": prompt}]
            )
            return response.content[0].text

        elif self.config.provider == 'openai':
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                max_tokens=max_tokens
            )
            return response.choices[0].message.content

        elif self.config.provider == 'ollama':
            response = requests.post(
                f"{self.base_url}/api/generate",
                json={
                    "model": self.model,
                    "prompt": prompt,
                    "stream": False
                }
            )
            return response.json().get('response', '')
        
        return ""


def enhance_with_llm(context: Dict[str, Any], provider: str, api_key: Optional[str] = None,
                     model: Optional[str] = None, base_url: Optional[str] = None) -> Dict[str, str]:
    """Enhance documentation with LLM using extracted facts."""
    if provider == 'none':
        return {}

    print(f"============================================================")
    print(f"🤖 Smart Documentation Generator")
    print(f"============================================================")
    print(f"✅ LLM Provider: {provider}")
    print(f"🚀 Enhancing documentation...")
    try:
        config = LLMConfig(provider=provider, api_key=api_key, model=model, base_url=base_url)
        client = LLMClient(config)

        # Prepare OPTIMIZED context for LLM with maximum relevant information
        repo = context.get('repository', {})
        tech = context.get('tech_overview', {})
        structure = context.get('structure', {})
        symbols = context.get('symbols', {})
        database = context.get('database', {})
        rules = context.get('functional_rules', [])
        stats = context.get('stats', {})

        # Organize classes by file for better context
        classes_by_file = defaultdict(list)
        for cls in symbols.get('classes', [])[:50]:
            classes_by_file[cls.get('file', 'unknown')].append(cls['name'])

        # Organize functions by file and class
        functions_by_context = defaultdict(list)
        for func in symbols.get('functions', [])[:100]:
            key = func.get('class', func.get('file', 'unknown'))
            functions_by_context[key].append(func['name'])

        # Group routes by path prefix for API structure understanding
        routes_by_prefix = defaultdict(list)
        for route in symbols.get('routes', []):
            path = route.get('path', '/')
            prefix = path.split('/')[1] if len(path.split('/')) > 1 else 'root'
            routes_by_prefix[prefix].append({
                'method': route.get('method'),
                'path': path,
                'handler': route.get('handler', 'unknown')
            })

        # Extract key technologies from manifests
        technologies = []
        for manifest in tech.get('manifests', []):
            if 'package.json' in manifest:
                technologies.append('Node.js/npm')
            elif 'requirements.txt' in manifest or 'pyproject.toml' in manifest:
                technologies.append('Python')
            elif 'build.gradle' in manifest or 'pom.xml' in manifest:
                technologies.append('Java/JVM')
            elif 'Cargo.toml' in manifest:
                technologies.append('Rust')
            elif 'go.mod' in manifest:
                technologies.append('Go')
            elif 'Dockerfile' in manifest:
                technologies.append('Docker')

        # Build enriched context
        enriched_context = {
            'project': {
                'name': repo.get('name', 'Unknown Project'),
                'path': repo.get('path', ''),
            },
            'statistics': {
                'total_files': stats.get('total_files', 0),
                'total_classes': stats.get('total_classes', 0),
                'total_functions': stats.get('total_functions', 0),
                'total_routes': stats.get('total_routes', 0),
                'total_tables': stats.get('total_tables', 0),
                'total_rules': stats.get('total_rules', 0),
            },
            'technology_stack': {
                'languages': tech.get('languages', {}),
                'detected_technologies': list(set(technologies)),
                'manifests': tech.get('manifests', []),
            },
            'architecture': {
                'top_level_directories': structure.get('top_level_dirs', []),
                'key_config_files': structure.get('key_files', []),
                'classes_by_file': dict(list(classes_by_file.items())[:20]),
                'function_contexts': dict(list(functions_by_context.items())[:30]),
            },
            'api_structure': {
                'route_groups': {k: v[:10] for k, v in routes_by_prefix.items()},
                'total_endpoints': len(symbols.get('routes', [])),
            },
            'data_layer': {
                'tables': [{'name': t['name'], 'type': t.get('type'), 'source': t.get('source')}
                          for t in database.get('tables', [])[:30]],
                'models': [{'class': m['class'], 'table': m['table']}
                          for m in database.get('models', [])[:30]],
                'has_migrations': len(database.get('migrations', [])) > 0,
            },
            'business_logic': {
                'validation_rules_count': len([r for r in rules if r.get('type') == 'validation']),
                'test_evidence_count': len([r for r in rules if r.get('type') == 'test_evidence']),
                'sample_rules': [{'id': r.get('id'), 'text': r.get('text')[:100], 'type': r.get('type')}
                                for r in rules[:15]],
            }
        }

        # OPTIMIZED PROMPT with structured instructions
        prompt = f"""You are an expert software architect and technical writer. Your task is to generate comprehensive, professional documentation for a software project based on extracted code analysis.

# PROJECT ANALYSIS DATA
{json.dumps(enriched_context, indent=2)}

# YOUR MISSION
Generate two complete markdown documents that will help developers understand and work with this codebase:

## 1. PROJECT_OVERVIEW.md Enhancement
Create a comprehensive project overview documentation that includes:

### Required Sections:
- **Project Summary**:
  * Name and purpose inferred from structure, APIs, and database schema
  * Primary programming language and framework
  * Architectural style (monolithic, microservices, layered, etc.)
- **Technical Stack**:
  * Programming languages with file counts
  * Frameworks and libraries (detected from manifests)
  * Build tools and dependency management
  * Database technology
  * Infrastructure (Docker, Kubernetes, etc.)
- **Codebase Statistics**:
  * Total files, classes, functions
  * Lines of code estimate
  * Test coverage indicators
  * API endpoints count
  * Database tables count
- **Module Structure**:
  * Top-level directories with their purposes
  * Key modules and their responsibilities
  * Dependencies between modules
- **Key Components**:
  * Main classes and their roles
  * Important functions/methods grouped by domain
  * Design patterns used
- **Configuration & Setup**:
  * Required environment variables
  * Configuration files location
  * Build and run commands
- **Testing Strategy**:
  * Test frameworks used
  * Test organization
  * How to run tests

### Style Guidelines:
- Focus on technical accuracy over marketing
- Use tables for structured data (languages, modules, components)
- Include file:line references for key components
- Be concise and factual
- Use code blocks only for actual commands or code snippets

## 2. ARCHITECTURE.md Enhancement
Create an in-depth architecture document that includes:

### Required Sections:
- **System Overview**: Purpose, scope, and architectural style
- **Architecture Diagram**: Mermaid diagram showing major components and their relationships
- **Component Breakdown**: Detailed description of each major component/module
  * Responsibility
  * Key classes/functions
  * Dependencies
- **Data Flow**: How data moves through the system (with sequence diagram)
- **API Layer**:
  * Complete endpoint listing grouped by domain
  * Request/response patterns
  * Authentication/authorization approach (if detectable)
- **Data Layer**:
  * Database schema (ER diagram in Mermaid)
  * ORM/data access patterns
  * Migration strategy
- **Business Logic Layer**: Key services, validation rules, business constraints
- **Technology Decisions**: Why these technologies make sense together
- **Design Patterns**: Detected patterns (MVC, Repository, Service Layer, etc.)
- **Security Considerations**: Based on detected validation rules
- **Performance Considerations**: Caching, database indexes, etc.
- **Scalability**: How the architecture supports growth
- **Testing Strategy**: Test file patterns and coverage areas

### Style Guidelines:
- Use Mermaid diagrams extensively (component, sequence, ER diagrams)
- Include code references (file:line format)
- Explain "why" not just "what"
- Connect technical decisions to business value
- Use subsections for readability

# OUTPUT FORMAT
Return a valid JSON object with exactly this structure. CRITICAL: Properly escape all special characters in the JSON strings (backslashes, quotes, newlines).

```json
{{
  "project_overview": "# Full PROJECT_OVERVIEW.md markdown content here...",
  "architecture_enhancement": "# Full ARCHITECTURE.md markdown content here..."
}}
```

# IMPORTANT CONSTRAINTS
- Use ONLY the provided data - do not invent features or technologies
- Infer purpose from structure, naming, routes, and database schema
- If something is unclear, state it professionally (e.g., "This appears to be...")
- Include ALL detected API routes in architecture doc
- Use proper markdown formatting with headers, lists, code blocks, and diagrams
- Make the documentation immediately useful for a new developer joining the project
- Total output should be comprehensive (aim for 300-500 lines per document)
- **JSON FORMATTING**: Escape newlines as \\n, quotes as \\", backslashes as \\\\
- **VALIDATE**: Ensure your JSON is valid before outputting

Generate the JSON response now. Output ONLY the JSON, nothing else:"""

        # Use higher token limit for comprehensive output
        response = client.analyze(prompt, max_tokens=8000)

        # Try to parse JSON from response (sometimes LLMs wrap it in code blocks)
        try:
            # Try to find JSON in code blocks first
            json_block_match = re.search(r'```(?:json)?\s*(\{.*?\})\s*```', response, re.DOTALL)
            if json_block_match:
                enhancements = json.loads(json_block_match.group(1))
                print("✅ Documentation enhanced successfully!")
                return enhancements

            # Fallback: try to find raw JSON
            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if json_match:
                enhancements = json.loads(json_match.group(0))
                print("✅ Documentation enhanced successfully!")
                return enhancements

            # If no JSON found, wrap the response
            print("⚠️  Could not parse JSON response, using raw content")
            return {
                'project_overview': response[:len(response)//2],
                'architecture_enhancement': response[len(response)//2:]
            }

        except json.JSONDecodeError as je:
            print(f"⚠️  JSON parse error: {je}")
            print("🔄 Attempting intelligent content extraction...")

            # Try to extract PROJECT_OVERVIEW and ARCHITECTURE sections intelligently
            overview_content = ""
            arch_content = ""

            # Look for markdown headers that indicate sections
            overview_match = re.search(r'(?:project_overview["\']\s*:\s*["\']|# Project|# .*Overview)', response, re.IGNORECASE)
            arch_match = re.search(r'(?:architecture_enhancement["\']\s*:\s*["\']|# Architecture|# System)', response, re.IGNORECASE)

            if overview_match and arch_match:
                # Split at architecture section
                overview_content = response[overview_match.start():arch_match.start()]
                arch_content = response[arch_match.start():]

                # Clean up JSON artifacts
                overview_content = re.sub(r'project_overview["\']\s*:\s*["\']', '', overview_content)
                overview_content = re.sub(r'["\']?\s*,?\s*architecture_enhancement', '', overview_content)
                arch_content = re.sub(r'architecture_enhancement["\']\s*:\s*["\']', '', arch_content)
                arch_content = re.sub(r'["\'}]+\s*$', '', arch_content)

                print("✅ Content extracted successfully!")
                return {
                    'project_overview': overview_content.strip(),
                    'architecture_enhancement': arch_content.strip()
                }

            # Ultimate fallback: split in half
            print("⚠️  Using fallback split strategy")
            parts = response.split('\n\n')
            mid = len(parts) // 2
            return {
                'project_overview': '\n\n'.join(parts[:mid]),
                'architecture_enhancement': '\n\n'.join(parts[mid:])
            }

    except Exception as e:
        print(f"❌ LLM Enhancement failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return {}

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
        '--api-key',
        type=str,
        help='API Key for the LLM provider'
    )
    parser.add_argument(
        '--model',
        type=str,
        help='Model name to use (e.g., gpt-4o, claude-3-5-sonnet-20241022)'
    )
    parser.add_argument(
        '--base-url',
        type=str,
        help='Base URL for Ollama or other providers'
    )
    parser.add_argument(
        '--max-files',
        type=int,
        default=400,
        help='Maximum number of files to process (default: 400)'
    )
    parser.add_argument(
        '--no-cache',
        action='store_true',
        help='Disable caching (default: caching enabled)'
    )
    parser.add_argument(
        '--clear-cache',
        action='store_true',
        help='Clear cache and exit'
    )
    parser.add_argument(
        '--include-pattern',
        type=str,
        help='Include only files matching regex pattern (e.g., ".*\\.py$" for Python only)'
    )
    parser.add_argument(
        '--exclude-pattern',
        type=str,
        help='Exclude files matching regex pattern (e.g., "test.*" to exclude test files)'
    )

    args = parser.parse_args()

    # Handle cache clearing
    if args.clear_cache:
        cache = CacheManager()
        cache.clear()
        print("✅ Cache cleared successfully")
        sys.exit(0)

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
    print(f"📁 Analyzing repository: {repo_path}")
    context = generate_doc_context(
        repo_path,
        args.max_files,
        use_cache=not args.no_cache,
        include_pattern=args.include_pattern,
        exclude_pattern=args.exclude_pattern
    )

    # Save doc_context.json
    context_file = out_path / 'doc_context.json'
    print(f"📄 Writing context to: {context_file}")
    with open(context_file, 'w', encoding='utf-8') as f:
        json.dump(context, f, indent=2)

    # Enhance with LLM (optional)
    enhancements = enhance_with_llm(
        context, 
        args.llm_provider, 
        api_key=args.api_key, 
        model=args.model, 
        base_url=args.base_url
    )
    
    # Merge enhancements into context for template use
    if enhancements:
        context['llm_enhanced'] = enhancements

    # Generate documentation files
    print("📝 Generating documentation...")

    # Create docs directory
    docs_dir = out_path / 'docs'
    docs_dir.mkdir(exist_ok=True)

    # Generate PROJECT_OVERVIEW.md
    overview_file = out_path / 'PROJECT_OVERVIEW.md'
    print(f"  └─ Writing: {overview_file}")
    with open(overview_file, 'w', encoding='utf-8') as f:
        f.write(generate_project_overview(context))

    # Generate architecture.md
    arch_file = docs_dir / 'architecture.md'
    print(f"  └─ Writing: {arch_file}")
    with open(arch_file, 'w', encoding='utf-8') as f:
        f.write(generate_architecture_md(context))

    # Generate database.md
    db_file = docs_dir / 'database.md'
    print(f"  └─ Writing: {db_file}")
    with open(db_file, 'w', encoding='utf-8') as f:
        f.write(generate_database_md(context))

    # Generate functional_rules.md
    rules_file = docs_dir / 'functional_rules.md'
    print(f"  └─ Writing: {rules_file}")
    with open(rules_file, 'w', encoding='utf-8') as f:
        f.write(generate_functional_rules_md(context))

    print("\n✅ Documentation generation complete!")
    print(f"\nGenerated files:")
    print(f"  - {overview_file}")
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
