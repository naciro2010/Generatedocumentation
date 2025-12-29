# Guide de Build

## Prérequis

- **Java 17** ou supérieur
- **Internet** (pour télécharger les dépendances)
- **Gradle** sera téléchargé automatiquement via le wrapper

## Build Rapide

```bash
# 1. Générer le Gradle Wrapper (première fois seulement)
gradle wrapper --gradle-version=8.5

# 2. Build complet
./gradlew build

# 3. Build sans tests (plus rapide)
./gradlew build -x test
```

## Build des Modules Individuels

```bash
# Core library (modèles + Evidence)
./gradlew :libs:core:build

# Plugins (JavaLegacyPlugin, PHPLegacyPlugin, etc.)
./gradlew :libs:plugins:build

# LLM integration
./gradlew :libs:llm:build

# Documentation generator
./gradlew :libs:docs:build
```

## Structure Simplifiée

Le projet est maintenant **sans Spring Boot** pour les librairies:

```
libs/
├── core/          # Domain model (Evidence, IR, Assertion)
├── parsing/       # Code parsing utilities
├── plugins/       # Analysis plugins (Java Legacy, PHP Legacy, etc.)
├── llm/           # LLM clients (OpenAI, Anthropic, Gemini, Bedrock, Mock)
├── docs/          # Documentation generation (Markdown, PDF)
└── graph-neo4j/   # Neo4j graph database (optional)
```

## Dépendances Principales

- **Kotlin 1.9.20**
- **Jackson 2.15.3** (JSON/YAML)
- **OkHttp 4.12.0** (HTTP client pour LLM)
- **OpenAI Client 3.6.2** (SDK communautaire)
- **AWS SDK 2.21.0** (pour Bedrock)
- **JUnit 5.9.2** (tests)

## Problèmes Courants

### "Plugin not found"

Si vous voyez des erreurs de type "Plugin [id: 'org.jetbrains.kotlin.jvm'] was not found":

1. Vérifiez votre connexion internet
2. Essayez avec le wrapper: `./gradlew build`
3. Nettoyez le cache Gradle: `rm -rf ~/.gradle/caches`

### "Cannot download dependencies"

Si les dépendances ne se téléchargent pas:

1. Vérifiez que `mavenCentral()` est accessible
2. Essayez avec un proxy si nécessaire
3. Vérifiez `~/.gradle/gradle.properties`

### Build Offline

Si vous avez déjà téléchargé les dépendances:

```bash
./gradlew build --offline
```

## Versions Testées

| Composant | Version | Status |
|-----------|---------|--------|
| Java | 17, 21 | ✅ Testé |
| Kotlin | 1.9.20 | ✅ Stable |
| Gradle | 8.5+ | ✅ Recommandé |
| Jackson | 2.15.3 | ✅ Stable |

## Next Steps

Une fois le build réussi:

1. Lisez [QUICKSTART.md](QUICKSTART.md) pour l'utilisation
2. Voir [README.md](README.md) pour la documentation complète
3. Exemples dans `samples/`
