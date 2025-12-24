package io.docgen.core.graph

/**
 * Modèle de graphe pour le Code Knowledge Graph.
 * Peut être stocké en Postgres (tables edges/nodes) ou Neo4j.
 */

data class CodeGraph(
    val projectId: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNode(
    val id: String,
    val type: NodeType,
    val name: String,
    val fqn: String,
    val properties: Map<String, Any> = emptyMap()
)

enum class NodeType {
    MODULE, SERVICE, ENDPOINT, ENTITY, JOB, EVENT, DEPENDENCY
}

data class GraphEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val type: EdgeType,
    val properties: Map<String, Any> = emptyMap()
)

enum class EdgeType {
    CALLS,
    DEPENDS_ON,
    EXPOSES,
    READS,
    WRITES,
    VALIDATES,
    TRIGGERS,
    SUBSCRIBES_TO,
    CONTAINS
}

/**
 * Service pour requêter le graphe.
 */
interface CodeGraphService {
    fun buildGraph(projectId: String): CodeGraph
    fun findDependents(nodeId: String): List<GraphNode>
    fun findDependencies(nodeId: String): List<GraphNode>
    fun findPath(fromId: String, toId: String): List<GraphEdge>?
    fun getSubgraph(nodeIds: List<String>): CodeGraph
}
