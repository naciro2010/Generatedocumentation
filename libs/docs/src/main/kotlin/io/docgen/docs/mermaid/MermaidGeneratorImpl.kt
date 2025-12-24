package io.docgen.docs.mermaid

import io.docgen.core.model.EntityIR
import io.docgen.core.model.ProjectIR
import io.docgen.docs.generator.MermaidGenerator

/**
 * Génère des diagrammes Mermaid.
 */
class MermaidGeneratorImpl : MermaidGenerator {

    override fun generateC4Context(projectIR: ProjectIR): String {
        return buildString {
            appendLine("graph TB")
            projectIR.modules.forEach { module ->
                appendLine("    ${sanitize(module.name)}[${module.name}<br/>Type: ${module.type}]")
            }

            // Add connections (simplified)
            projectIR.modules.forEachIndexed { index, module ->
                if (index > 0) {
                    appendLine("    ${sanitize(projectIR.modules[index - 1].name)} --> ${sanitize(module.name)}")
                }
            }
        }
    }

    override fun generateERDiagram(entities: List<EntityIR>): String {
        return buildString {
            appendLine("erDiagram")
            entities.forEach { entity ->
                val tableName = entity.table ?: entity.name
                appendLine("    $tableName {")
                entity.fields.forEach { field ->
                    val pk = if (field.isPrimaryKey) "PK" else ""
                    val fk = if (field.isForeignKey) "FK" else ""
                    val key = listOfNotNull(pk, fk).joinToString(",")
                    appendLine("        ${field.type} ${field.name} ${key}")
                }
                appendLine("    }")
            }

            // Add relationships
            entities.forEach { entity ->
                entity.relations.forEach { relation ->
                    val relationSymbol = when (relation.type) {
                        io.docgen.core.model.RelationType.ONE_TO_ONE -> "||--||"
                        io.docgen.core.model.RelationType.ONE_TO_MANY -> "||--o{"
                        io.docgen.core.model.RelationType.MANY_TO_ONE -> "}o--||"
                        io.docgen.core.model.RelationType.MANY_TO_MANY -> "}o--o{"
                    }
                    val sourceTable = entity.table ?: entity.name
                    appendLine("    $sourceTable $relationSymbol ${relation.targetEntity} : ${relation.fieldName}")
                }
            }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_]"), "_")
}
