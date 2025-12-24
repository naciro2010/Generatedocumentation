package io.docgen.graph.neo4j.model

import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Relationship

@Node("CodeNode")
data class CodeNodeEntity(
    @Id
    val id: String,
    val type: String,
    val name: String,
    val fqn: String,
    val projectId: String,
    val properties: Map<String, String> = emptyMap()
)

@Node("CodeEdge")
data class CodeEdgeEntity(
    @Id
    val id: String,
    val projectId: String,
    val relationType: String,

    @Relationship(type = "FROM")
    val source: CodeNodeEntity,

    @Relationship(type = "TO")
    val target: CodeNodeEntity,

    val properties: Map<String, String> = emptyMap()
)
