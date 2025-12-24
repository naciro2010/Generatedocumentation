package io.docgen.graph.neo4j.repository

import io.docgen.graph.neo4j.model.CodeEdgeEntity
import io.docgen.graph.neo4j.model.CodeNodeEntity
import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.data.neo4j.repository.query.Query
import org.springframework.stereotype.Repository

@Repository
interface CodeNodeNeo4jRepository : Neo4jRepository<CodeNodeEntity, String> {
    fun findByProjectId(projectId: String): List<CodeNodeEntity>

    @Query("""
        MATCH (n:CodeNode {id: ${'$'}nodeId})<-[:TO]-(e:CodeEdge)-[:FROM]->(dependent:CodeNode)
        RETURN dependent
    """)
    fun findDependents(nodeId: String): List<CodeNodeEntity>

    @Query("""
        MATCH (n:CodeNode {id: ${'$'}nodeId})-[:FROM]->(e:CodeEdge)-[:TO]->(dependency:CodeNode)
        RETURN dependency
    """)
    fun findDependencies(nodeId: String): List<CodeNodeEntity>
}

@Repository
interface CodeEdgeNeo4jRepository : Neo4jRepository<CodeEdgeEntity, String> {
    fun findByProjectId(projectId: String): List<CodeEdgeEntity>

    @Query("""
        MATCH path = shortestPath(
            (start:CodeNode {id: ${'$'}fromId})-[:FROM|TO*]-(end:CodeNode {id: ${'$'}toId})
        )
        RETURN relationships(path)
    """)
    fun findShortestPath(fromId: String, toId: String): List<CodeEdgeEntity>?

    @Query("""
        MATCH (n:CodeNode)
        WHERE n.id IN ${'$'}nodeIds
        MATCH (n)-[r:FROM|TO]-(m:CodeNode)
        WHERE m.id IN ${'$'}nodeIds
        RETURN r
    """)
    fun findEdgesBetweenNodes(nodeIds: List<String>): List<CodeEdgeEntity>
}
