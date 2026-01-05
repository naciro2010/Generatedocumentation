# 🤖 Smart Documentation Generator

> **Générateur intelligent de documentation avec IA obligatoire**
> Crée une vraie documentation métier qui explique le **contexte, les règles, l'architecture** - pas juste des listes de fichiers!

[![Python 3.8+](https://img.shields.io/badge/python-3.8+-blue.svg)](https://www.python.org/downloads/)
[![AI Powered](https://img.shields.io/badge/AI-Required-green.svg)](https://anthropic.com)

---

## 🎯 Ce qui a changé

### ❌ Ancien générateur (generate_docs.py)
- Liste des fichiers et fonctions
- Aucun contexte métier
- Documentation inutile
- LLM optionnel (et non fonctionnel)

### ✅ Nouveau générateur (smart_doc_generator.py)
- **IA obligatoire** - comprend vraiment le code
- **Contexte métier** - explique le POURQUOI
- **Architecture réelle** - entry points, data flow, composants
- **Règles métier** - validations, workflows, contraintes
- **Intégrations** - APIs, BDD, providers
- **Sortie organisée** dans `AutoDoc_LLM/`

---

## 🚀 Installation Rapide

```bash
# 1. Dépendances minimales
pip3 install anthropic  # ou openai, ou requests (pour Ollama)

# 2. Configurer la clé API
export ANTHROPIC_API_KEY="sk-ant-api03-xxxxx"

# 3. Générer la doc (IA OBLIGATOIRE)
python3 smart_doc_generator.py --repo . --provider anthropic

# 4. Lire la doc
cd AutoDoc_LLM/
ls -l
```

**Résultat:** 5 fichiers Markdown avec du vrai contenu métier!

---

## 📖 Usage

### Option 1: Claude (Anthropic) - Recommandé ✅

```bash
# Installer
pip3 install anthropic

# Configurer
export ANTHROPIC_API_KEY="sk-ant-api03-xxxxx"

# Lancer
python3 smart_doc_generator.py \
  --repo /path/to/project \
  --provider anthropic
```

**Obtenir une clé:** https://console.anthropic.com/

### Option 2: OpenAI (GPT-4)

```bash
# Installer
pip3 install openai

# Configurer
export OPENAI_API_KEY="sk-xxxxx"

# Lancer
python3 smart_doc_generator.py \
  --repo /path/to/project \
  --provider openai
```

**Obtenir une clé:** https://platform.openai.com/api-keys

### Option 3: Ollama (Local & Gratuit) 🆓

```bash
# Installer Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Télécharger un modèle
ollama pull llama3.2

# Lancer le serveur (terminal 1)
ollama serve

# Générer la doc (terminal 2)
python3 smart_doc_generator.py \
  --repo /path/to/project \
  --provider ollama
```

---

## 📂 Documentation Générée

La doc est créée dans **`AutoDoc_LLM/`** avec 5 fichiers :

```
AutoDoc_LLM/
├── 01_Overview.md          # Vue d'ensemble métier
│   ├── Quel problème résout l'app?
│   ├── Domaines métier principaux
│   ├── Caractéristiques techniques
│   └── Fonctionnalités clés
│
├── 02_Architecture.md      # Architecture technique
│   ├── Style d'architecture
│   ├── Points d'entrée (APIs, CLI, UI)
│   ├── Composants principaux
│   ├── Flux de données
│   ├── Dépendances externes
│   └── Diagramme Mermaid
│
├── 03_Business_Rules.md    # Règles métier
│   ├── Règles de validation
│   ├── Contraintes métier
│   ├── Workflows
│   ├── Règles d'autorisation
│   └── Règles de calcul
│
├── 04_Integrations.md      # Intégrations
│   ├── API REST endpoints
│   ├── Schéma base de données
│   ├── APIs externes utilisées
│   ├── Message queues / Events
│   └── Fichiers I/O
│
├── 05_Deployment.md        # Déploiement
│   ├── Prérequis
│   ├── Configuration
│   ├── Installation
│   ├── Lancement
│   ├── Build production
│   └── Options de déploiement
│
└── context.json            # Contexte brut (debugging)
```

---

## 💡 Exemples d'Utilisation

### Projet Spring Boot

```bash
export ANTHROPIC_API_KEY="sk-ant-..."

python3 smart_doc_generator.py \
  --repo ./my-spring-api \
  --provider anthropic \
  --max-files 150

cd AutoDoc_LLM/
cat 01_Overview.md
```

### Projet Node.js/TypeScript

```bash
export OPENAI_API_KEY="sk-..."

python3 smart_doc_generator.py \
  --repo ./my-node-app \
  --provider openai \
  --model gpt-4o

open AutoDoc_LLM/02_Architecture.md
```

### Gros Projet (avec Ollama gratuit)

```bash
# Lancer Ollama
ollama serve &

python3 smart_doc_generator.py \
  --repo /path/to/large/project \
  --provider ollama \
  --model llama3.2 \
  --max-files 200
```

---

## 🔧 Options Complètes

```bash
python3 smart_doc_generator.py --help

Options:
  --repo PATH           Chemin du projet (défaut: .)
  --provider PROVIDER   LLM provider (OBLIGATOIRE)
                        Choix: anthropic, openai, ollama
  --api-key KEY         Clé API (ou variable d'environnement)
  --model MODEL         Nom du modèle (optionnel, utilise défauts)
  --max-files N         Nombre max d'échantillons de code (défaut: 100)
```

---

## 🎯 Pourquoi ce générateur?

### Problème avec les générateurs classiques:

```markdown
# Documentation inutile

## Fichiers
- src/main/UserService.java
- src/main/OrderController.java
- src/test/UserTest.java

## Fonctions
- getUserById(id: Long): User
- createOrder(order: Order): void
```

❌ **Ça ne sert à rien!** Aucun contexte, aucune explication métier.

### Solution avec Smart Doc Generator:

```markdown
# Overview

## What does this application do?

This is an **e-commerce order management system** that allows
customers to browse products, place orders, and track shipments.

The system handles:
- Customer registration and authentication
- Product catalog management
- Real-time inventory tracking
- Payment processing via Stripe
- Order fulfillment workflows

Target users: End customers (web/mobile) and back-office staff.

## Overview

This is a Kotlin-based application built with Gradle, containerized with Docker for easy deployment and scalability.

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java Development Kit (JDK 11 or higher)
- Gradle (or use the Gradle wrapper included in the project)

### Building the Project

```bash
./gradlew build
```

### Running with Docker

```bash
docker-compose up
```

The application will start in a containerized environment with all dependencies properly configured.

## Project Structure

```
.
├── src/                    # Application source code
├── gradle/                 # Gradle wrapper files
├── build.gradle.kts       # Gradle build configuration (Kotlin DSL)
├── Dockerfile             # Container image definition
├── docker-compose.yml     # Multi-container orchestration
└── README.md             # This file
```

## Development

### Build Configuration

The project uses **Gradle** with Kotlin DSL (`build.gradle.kts`) for dependency management and build automation.

### Code Organization

Source code is organized in the `src/` directory following standard Kotlin project conventions.

## Deployment

The project includes Docker support for containerized deployment:

- **Dockerfile**: Defines the application container image
- **docker-compose.yml**: Orchestrates multi-container deployment

Deploy to any Docker-compatible environment (Kubernetes, Docker Swarm, cloud platforms, etc.).

## Contributing

Following Kotlin best practices and project conventions when contributing code.

## License

Refer to LICENSE file for licensing information.