rootProject.name = "universal-doc-generator"

include(
    "apps:api",
    "apps:worker",
    "libs:core",
    "libs:parsing",
    "libs:plugins",
    "libs:llm",
    "libs:docs",
    "libs:graph-neo4j"
)
