#!/usr/bin/env python3
"""
Documentation Generator - Analyzes legacy code and generates README.md using AI

Supports: Struts 1.x/2.x, PHP Legacy, Spring Boot
"""

import os
import re
import json
import argparse
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Dict, Optional, Tuple
from anthropic import Anthropic


# ============================================================================
# DATA MODELS
# ============================================================================

@dataclass
class ProjectFingerprint:
    """Detected technologies and patterns in the project"""
    framework: str  # "struts", "php", "spring", "unknown"
    confidence: float  # 0.0 to 1.0
    files_found: List[str] = field(default_factory=list)
    patterns_matched: List[str] = field(default_factory=list)


@dataclass
class Endpoint:
    """Represents an API endpoint or web page"""
    method: str  # GET, POST, etc. or "PAGE" for PHP
    path: str
    handler: str
    file_path: str
    line_number: Optional[int] = None


@dataclass
class DatabaseQuery:
    """Represents a database query found in code"""
    query: str
    file_path: str
    line_number: int
    query_type: str  # SELECT, INSERT, UPDATE, DELETE


@dataclass
class SecurityIssue:
    """Represents a potential security vulnerability"""
    severity: str  # "high", "medium", "low"
    type: str  # "sql_injection", "xss", etc.
    description: str
    file_path: str
    line_number: int


@dataclass
class ProjectStructure:
    """Extracted structure from the codebase"""
    fingerprint: ProjectFingerprint
    endpoints: List[Endpoint] = field(default_factory=list)
    database_queries: List[DatabaseQuery] = field(default_factory=list)
    security_issues: List[SecurityIssue] = field(default_factory=list)
    total_files: int = 0
    total_lines: int = 0


# ============================================================================
# FRAMEWORK DETECTOR
# ============================================================================

class FrameworkDetector:
    """Detects which framework is used in the repository"""

    @staticmethod
    def detect(repo_path: Path) -> ProjectFingerprint:
        """Detect the main framework used in the project"""
        scores = {
            "struts": FrameworkDetector._detect_struts(repo_path),
            "php": FrameworkDetector._detect_php(repo_path),
            "spring": FrameworkDetector._detect_spring(repo_path),
        }

        framework = max(scores, key=scores.get)
        confidence = scores[framework]

        if confidence < 0.3:
            framework = "unknown"

        return ProjectFingerprint(
            framework=framework,
            confidence=confidence,
            files_found=[],
            patterns_matched=[]
        )

    @staticmethod
    def _detect_struts(repo_path: Path) -> float:
        """Detect Struts framework"""
        score = 0.0

        # Check for struts-config.xml
        if list(repo_path.rglob("struts-config.xml")):
            score += 0.5

        # Check for Action classes
        for java_file in repo_path.rglob("*.java"):
            try:
                content = java_file.read_text(encoding='utf-8', errors='ignore')
                if re.search(r'extends\s+Action', content):
                    score += 0.3
                    break
            except:
                continue

        return min(score, 1.0)

    @staticmethod
    def _detect_php(repo_path: Path) -> float:
        """Detect PHP legacy code"""
        score = 0.0

        php_files = list(repo_path.rglob("*.php"))
        if php_files:
            score += 0.6

        # Check for legacy mysql_query usage
        for php_file in php_files[:10]:  # Check first 10 files
            try:
                content = php_file.read_text(encoding='utf-8', errors='ignore')
                if 'mysql_query' in content or 'mysqli_query' in content:
                    score += 0.3
                    break
            except:
                continue

        return min(score, 1.0)

    @staticmethod
    def _detect_spring(repo_path: Path) -> float:
        """Detect Spring Boot"""
        score = 0.0

        # Check for Spring Boot markers
        if list(repo_path.rglob("application.yml")) or list(repo_path.rglob("application.properties")):
            score += 0.4

        # Check for @RestController
        for java_file in repo_path.rglob("*.java"):
            try:
                content = java_file.read_text(encoding='utf-8', errors='ignore')
                if '@RestController' in content or '@Controller' in content:
                    score += 0.5
                    break
            except:
                continue

        return min(score, 1.0)


# ============================================================================
# CODE EXTRACTOR
# ============================================================================

class CodeExtractor:
    """Extracts code structure based on detected framework"""

    MAX_FILES_PER_TYPE = 100  # Limit to prevent excessive processing

    @staticmethod
    def extract(repo_path: Path, fingerprint: ProjectFingerprint) -> ProjectStructure:
        """Extract code structure based on framework"""

        if fingerprint.framework == "struts":
            return CodeExtractor._extract_struts(repo_path, fingerprint)
        elif fingerprint.framework == "php":
            return CodeExtractor._extract_php(repo_path, fingerprint)
        elif fingerprint.framework == "spring":
            return CodeExtractor._extract_spring(repo_path, fingerprint)
        else:
            return CodeExtractor._extract_generic(repo_path, fingerprint)

    @staticmethod
    def _extract_struts(repo_path: Path, fingerprint: ProjectFingerprint) -> ProjectStructure:
        """Extract Struts endpoints from struts-config.xml and Action classes"""
        structure = ProjectStructure(fingerprint=fingerprint)

        # Parse struts-config.xml
        for config_file in repo_path.rglob("struts-config.xml"):
            try:
                content = config_file.read_text(encoding='utf-8')
                # Find action mappings: <action path="/login" type="com.example.LoginAction"/>
                for match in re.finditer(r'<action\s+path="([^"]+)"\s+type="([^"]+)"', content):
                    path, handler = match.groups()
                    structure.endpoints.append(Endpoint(
                        method="POST",
                        path=path,
                        handler=handler.split('.')[-1],
                        file_path=str(config_file.relative_to(repo_path))
                    ))
            except:
                continue

        # Extract SQL queries from Action classes
        for java_file in list(repo_path.rglob("*Action.java"))[:CodeExtractor.MAX_FILES_PER_TYPE]:
            CodeExtractor._extract_sql_from_java(java_file, repo_path, structure)

        CodeExtractor._count_files_and_lines(repo_path, structure, "*.java")

        return structure

    @staticmethod
    def _extract_php(repo_path: Path, fingerprint: ProjectFingerprint) -> ProjectStructure:
        """Extract PHP pages and SQL queries"""
        structure = ProjectStructure(fingerprint=fingerprint)

        php_files = list(repo_path.rglob("*.php"))[:CodeExtractor.MAX_FILES_PER_TYPE]

        for php_file in php_files:
            try:
                content = php_file.read_text(encoding='utf-8', errors='ignore')
                rel_path = str(php_file.relative_to(repo_path))

                # Treat each PHP file as a page endpoint
                structure.endpoints.append(Endpoint(
                    method="PAGE",
                    path=f"/{php_file.stem}.php",
                    handler=php_file.name,
                    file_path=rel_path
                ))

                # Extract SQL queries
                CodeExtractor._extract_sql_from_php(php_file, repo_path, structure, content)

                # Check for SQL injection vulnerabilities
                CodeExtractor._check_php_security(php_file, repo_path, structure, content)

            except:
                continue

        CodeExtractor._count_files_and_lines(repo_path, structure, "*.php")

        return structure

    @staticmethod
    def _extract_spring(repo_path: Path, fingerprint: ProjectFingerprint) -> ProjectStructure:
        """Extract Spring Boot REST endpoints"""
        structure = ProjectStructure(fingerprint=fingerprint)

        for java_file in list(repo_path.rglob("*.java"))[:CodeExtractor.MAX_FILES_PER_TYPE]:
            try:
                content = java_file.read_text(encoding='utf-8', errors='ignore')
                rel_path = str(java_file.relative_to(repo_path))

                # Find @GetMapping, @PostMapping, etc.
                for match in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\("([^"]+)"\)', content):
                    method, path = match.groups()
                    structure.endpoints.append(Endpoint(
                        method=method.upper(),
                        path=path,
                        handler=java_file.stem,
                        file_path=rel_path
                    ))

                # Extract SQL queries
                CodeExtractor._extract_sql_from_java(java_file, repo_path, structure)

            except:
                continue

        CodeExtractor._count_files_and_lines(repo_path, structure, "*.java")

        return structure

    @staticmethod
    def _extract_generic(repo_path: Path, fingerprint: ProjectFingerprint) -> ProjectStructure:
        """Generic extraction for unknown frameworks"""
        structure = ProjectStructure(fingerprint=fingerprint)
        CodeExtractor._count_files_and_lines(repo_path, structure, "*.*")
        return structure

    @staticmethod
    def _extract_sql_from_java(java_file: Path, repo_path: Path, structure: ProjectStructure):
        """Extract SQL queries from Java files"""
        try:
            content = java_file.read_text(encoding='utf-8', errors='ignore')
            lines = content.split('\n')

            for i, line in enumerate(lines, 1):
                # Find SQL strings
                for match in re.finditer(r'"(SELECT|INSERT|UPDATE|DELETE)\s+[^"]{10,}"', line, re.IGNORECASE):
                    query = match.group(1)
                    structure.database_queries.append(DatabaseQuery(
                        query=query[:100] + "..." if len(query) > 100 else query,
                        file_path=str(java_file.relative_to(repo_path)),
                        line_number=i,
                        query_type=query.split()[0].upper()
                    ))
        except:
            pass

    @staticmethod
    def _extract_sql_from_php(php_file: Path, repo_path: Path, structure: ProjectStructure, content: str):
        """Extract SQL queries from PHP files"""
        lines = content.split('\n')

        for i, line in enumerate(lines, 1):
            # Find mysql_query or mysqli_query calls
            if 'mysql_query' in line or 'mysqli_query' in line:
                # Try to extract the query string
                match = re.search(r'["\']((SELECT|INSERT|UPDATE|DELETE)\s+[^"\']{10,})["\']', line, re.IGNORECASE)
                if match:
                    query = match.group(1)
                    structure.database_queries.append(DatabaseQuery(
                        query=query[:100] + "..." if len(query) > 100 else query,
                        file_path=str(php_file.relative_to(repo_path)),
                        line_number=i,
                        query_type=query.split()[0].upper()
                    ))

    @staticmethod
    def _check_php_security(php_file: Path, repo_path: Path, structure: ProjectStructure, content: str):
        """Check for common PHP security issues"""
        lines = content.split('\n')

        for i, line in enumerate(lines, 1):
            # SQL Injection: Direct variable interpolation in queries
            if re.search(r'(mysql_query|mysqli_query).*\$_(GET|POST|REQUEST)', line, re.IGNORECASE):
                structure.security_issues.append(SecurityIssue(
                    severity="high",
                    type="sql_injection",
                    description="Potential SQL injection: user input directly in query",
                    file_path=str(php_file.relative_to(repo_path)),
                    line_number=i
                ))

            # XSS: Unescaped echo
            if re.search(r'echo\s+\$_(GET|POST|REQUEST)', line):
                structure.security_issues.append(SecurityIssue(
                    severity="medium",
                    type="xss",
                    description="Potential XSS: unescaped user input in output",
                    file_path=str(php_file.relative_to(repo_path)),
                    line_number=i
                ))

    @staticmethod
    def _count_files_and_lines(repo_path: Path, structure: ProjectStructure, pattern: str):
        """Count total files and lines of code"""
        files = list(repo_path.rglob(pattern))
        structure.total_files = len(files)

        total_lines = 0
        for file in files[:CodeExtractor.MAX_FILES_PER_TYPE]:
            try:
                total_lines += len(file.read_text(encoding='utf-8', errors='ignore').split('\n'))
            except:
                continue

        structure.total_lines = total_lines


# ============================================================================
# LLM ENRICHER
# ============================================================================

class LLMEnricher:
    """Uses Claude AI to enrich the documentation"""

    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.getenv('ANTHROPIC_API_KEY')
        self.client = Anthropic(api_key=self.api_key) if self.api_key else None

    def enrich(self, structure: ProjectStructure) -> Dict[str, str]:
        """
        Enrich the extracted structure with AI analysis

        Returns:
            Dict with keys: 'overview', 'features', 'tech_stack', 'recommendations'
        """

        if not self.client:
            return self._generate_fallback(structure)

        try:
            # Prepare the prompt
            prompt = self._build_prompt(structure)

            # Call Claude API
            message = self.client.messages.create(
                model="claude-3-5-sonnet-20241022",
                max_tokens=2000,
                messages=[{
                    "role": "user",
                    "content": prompt
                }]
            )

            response_text = message.content[0].text

            return self._parse_response(response_text)

        except Exception as e:
            print(f"Warning: LLM enrichment failed: {e}")
            return self._generate_fallback(structure)

    def _build_prompt(self, structure: ProjectStructure) -> str:
        """Build the prompt for Claude"""

        endpoints_summary = f"\n- {len(structure.endpoints)} endpoints/pages found"
        if structure.endpoints[:5]:
            endpoints_summary += "\nExamples:\n"
            for ep in structure.endpoints[:5]:
                endpoints_summary += f"  - {ep.method} {ep.path} ({ep.handler})\n"

        queries_summary = f"\n- {len(structure.database_queries)} database queries found"
        security_summary = f"\n- {len(structure.security_issues)} security issues detected"

        return f"""Analyze this codebase and provide a technical summary:

Framework: {structure.fingerprint.framework.upper()} (confidence: {structure.fingerprint.confidence:.0%})
Files: {structure.total_files} files, ~{structure.total_lines} lines of code
{endpoints_summary}
{queries_summary}
{security_summary}

Provide:
1. **Overview**: What does this application do? (2-3 sentences)
2. **Main Features**: List 3-5 main features based on endpoints
3. **Technical Stack**: Technologies used
4. **Recommendations**: 2-3 suggestions for improvement

Format as:
## Overview
[text]

## Main Features
- Feature 1
- Feature 2

## Technical Stack
- Tech 1
- Tech 2

## Recommendations
- Recommendation 1
- Recommendation 2
"""

    def _parse_response(self, response: str) -> Dict[str, str]:
        """Parse Claude's response into sections"""
        sections = {
            'overview': '',
            'features': '',
            'tech_stack': '',
            'recommendations': ''
        }

        current_section = None
        lines = response.split('\n')

        for line in lines:
            if '## Overview' in line:
                current_section = 'overview'
            elif '## Main Features' in line:
                current_section = 'features'
            elif '## Technical Stack' in line:
                current_section = 'tech_stack'
            elif '## Recommendations' in line:
                current_section = 'recommendations'
            elif current_section and line.strip():
                sections[current_section] += line + '\n'

        return sections

    def _generate_fallback(self, structure: ProjectStructure) -> Dict[str, str]:
        """Generate basic documentation without LLM"""

        return {
            'overview': f"This is a {structure.fingerprint.framework.upper()} application with {structure.total_files} files and approximately {structure.total_lines} lines of code.",
            'features': "- Web application endpoints\n- Database integration\n- Business logic processing",
            'tech_stack': f"- Framework: {structure.fingerprint.framework.upper()}\n- Database: SQL\n- Language: {'Java' if structure.fingerprint.framework in ['struts', 'spring'] else 'PHP'}",
            'recommendations': "- Review security vulnerabilities\n- Add automated tests\n- Update dependencies"
        }


# ============================================================================
# README GENERATOR
# ============================================================================

class ReadmeGenerator:
    """Generates the final README.md"""

    @staticmethod
    def generate(structure: ProjectStructure, enrichment: Dict[str, str], output_path: Path):
        """Generate README.md file"""

        content = f"""# Project Documentation

## Overview

{enrichment['overview'].strip()}

**Framework**: {structure.fingerprint.framework.upper()} (confidence: {structure.fingerprint.confidence:.0%})
**Size**: {structure.total_files} files, ~{structure.total_lines} lines of code

## Main Features

{enrichment['features'].strip()}

## API Endpoints / Pages

Total: {len(structure.endpoints)} endpoints

"""

        # Add endpoints table
        if structure.endpoints:
            content += "| Method | Path | Handler |\n"
            content += "|--------|------|----------|\n"
            for ep in structure.endpoints[:20]:  # Show first 20
                content += f"| {ep.method} | {ep.path} | {ep.handler} |\n"

            if len(structure.endpoints) > 20:
                content += f"\n*...and {len(structure.endpoints) - 20} more endpoints*\n"

        # Add security section
        if structure.security_issues:
            content += f"\n## ⚠️ Security Concerns\n\n"
            content += f"Found {len(structure.security_issues)} potential security issues:\n\n"

            for issue in structure.security_issues[:10]:  # Show first 10
                content += f"- **{issue.type.upper()}** ({issue.severity}): {issue.description}\n"
                content += f"  - Location: `{issue.file_path}:{issue.line_number}`\n"

            if len(structure.security_issues) > 10:
                content += f"\n*...and {len(structure.security_issues) - 10} more issues*\n"

        # Add database section
        if structure.database_queries:
            content += f"\n## Database\n\n"
            content += f"Found {len(structure.database_queries)} SQL queries:\n\n"

            query_types = {}
            for query in structure.database_queries:
                query_types[query.query_type] = query_types.get(query.query_type, 0) + 1

            for qtype, count in query_types.items():
                content += f"- {qtype}: {count} queries\n"

        # Add technical stack
        content += f"\n## Technical Stack\n\n{enrichment['tech_stack'].strip()}\n"

        # Add recommendations
        content += f"\n## Recommendations\n\n{enrichment['recommendations'].strip()}\n"

        # Add metadata
        content += f"""

---

*Documentation generated automatically by [Documentation Generator](https://github.com/your-repo)*
"""

        # Write to file
        output_path.write_text(content, encoding='utf-8')
        print(f"✅ Documentation generated: {output_path}")


# ============================================================================
# MAIN
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Generate README.md for legacy code repositories using AI"
    )
    parser.add_argument(
        "--repo",
        type=Path,
        default=Path.cwd(),
        help="Path to repository (default: current directory)"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("README.md"),
        help="Output file path (default: README.md)"
    )
    parser.add_argument(
        "--api-key",
        help="Anthropic API key (alternative to ANTHROPIC_API_KEY env var)"
    )

    args = parser.parse_args()

    # Validate repository path
    if not args.repo.exists():
        print(f"❌ Error: Repository path does not exist: {args.repo}")
        return 1

    print(f"📁 Analyzing repository: {args.repo}")

    # Step 1: Detect framework
    print("🔍 Detecting framework...")
    detector = FrameworkDetector()
    fingerprint = detector.detect(args.repo)
    print(f"   → Found: {fingerprint.framework.upper()} (confidence: {fingerprint.confidence:.0%})")

    # Step 2: Extract code structure
    print("📊 Extracting code structure...")
    extractor = CodeExtractor()
    structure = extractor.extract(args.repo, fingerprint)
    print(f"   → Endpoints: {len(structure.endpoints)}")
    print(f"   → Database queries: {len(structure.database_queries)}")
    print(f"   → Security issues: {len(structure.security_issues)}")
    print(f"   → Total: {structure.total_files} files, ~{structure.total_lines} LOC")

    # Step 3: Enrich with LLM
    print("🤖 Enriching with AI analysis...")
    enricher = LLMEnricher(api_key=args.api_key)
    enrichment = enricher.enrich(structure)

    if enricher.client:
        print("   → AI analysis complete")
    else:
        print("   → Using fallback (no API key provided)")

    # Step 4: Generate README
    print("📝 Generating documentation...")
    generator = ReadmeGenerator()
    generator.generate(structure, enrichment, args.output)

    print(f"\n✨ Done! Documentation generated at: {args.output}")

    return 0


if __name__ == "__main__":
    exit(main())
