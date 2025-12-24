package io.docgen.graph.neo4j.service

import io.docgen.core.graph.*
import io.docgen.graph.neo4j.model.CodeEdgeEntity
import io.docgen.graph.neo4j.model.CodeNodeEntity
import io.docgen.graph.neo4j.repository.CodeEdgeNeo4jRepository
import io.docgen.graph.neo4j.repository.CodeNodeNeo4jRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class Neo4jCodeGraphService(
    private val nodeRepository: CodeNodeNeo4jRepository,
    private val edgeRepository: CodeEdgeNeo4jRepository
) : CodeGraphService {

    override fun buildGraph(projectId: String): CodeGraph {
        val nodes = nodeRepository.findByProjectId(projectId)
            .map { it.toDomain() }
        val edges = edgeRepository.findByProjectId(projectId)
            .map { it.toDomain() }

        return CodeGraph(
            projectId = projectId,
            nodes = nodes,
            edges = edges
        )
    }

    override fun findDependents(nodeId: String): List<GraphNode> {
        return nodeRepository.findDependents(nodeId)
            .map { it.toDomain() }
    }

    override fun findDependencies(nodeId: String): List<GraphNode> {
        return nodeRepository.findDependencies(nodeId)
            .map { it.toDomain() }
    }

    override fun findPath(fromId: String, toId: String): List<GraphEdge>? {
        val path = edgeRepository.findShortestPath(fromId, toId)
        return path?.map { it.toDomain() }
    }

    override fun getSubgraph(nodeIds: List<String>): CodeGraph {
        val nodes = nodeRepository.findAllById(nodeIds)
            .map { it.toDomain() }
        val edges = edgeRepository.findEdgesBetweenNodes(nodeIds)
            .map { it.toDomain() }

        return CodeGraph(
            projectId = nodes.firstOrNull()?.properties?.get("projectId") ?: "",
            nodes = nodes,
            edges = edges
        )
    }

    fun saveGraph(graph: CodeGraph) {
        // Convert and save nodes
        val nodeEntities = graph.nodes.map { it.toEntity(graph.projectId) }
        nodeRepository.saveAll(nodeEntities)

        // Convert and save edges
        val nodeMap = nodeEntities.associateBy { it.id }
        val edgeEntities = graph.edges.mapNotNull { edge ->
            val source = nodeMap[edge.sourceId] ?: return@mapNotNull null
            val target = nodeMap[edge.targetId] ?: return@mapNotNull null
            edge.toEntity(graph.projectId, source, target)
        }
        edgeRepository.saveAll(edgeEntities)
    }
}

// Extension functions for conversion
private fun CodeNodeEntity.toDomain() = GraphNode(
    id = id,
    type = NodeType.valueOf(type),
    name = name,
    fqn = fqn,
    properties = properties
)

private fun GraphNode.toEntity(projectId: String) = CodeNodeEntity(
    id = id,
    type = type.name,
    name = name,
    fqn = fqn,
    projectId = projectId,
    properties = properties.mapValues { it.value.toString() }
)

private fun CodeEdgeEntity.toDomain() = GraphEdge(
    id = id,
    sourceId = source.id,
    targetId = target.id,
    type = EdgeType.valueOf(relationType),
    properties = properties
)

private fun GraphEdge.toEntity(projectId: String, source: CodeNodeEntity, target: CodeNodeEntity) =
    CodeEdgeEntity(
        id = id,
        projectId = projectId,
        relationType = type.name,
        source = source,
        target = target,
        properties = properties.mapValues { it.value.toString() }
    )
