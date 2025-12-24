package io.docgen.core.repository

import io.docgen.core.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProjectRepository : JpaRepository<Project, String> {
    fun findByStatus(status: ProjectStatus): List<Project>
}

@Repository
interface ModuleRepository : JpaRepository<Module, String> {
    fun findByProjectId(projectId: String): List<Module>
}

@Repository
interface DependencyRepository : JpaRepository<Dependency, String> {
    fun findByProjectId(projectId: String): List<Dependency>
}

@Repository
interface CodeSymbolRepository : JpaRepository<CodeSymbol, String> {
    fun findByModuleId(moduleId: String): List<CodeSymbol>
    fun findByFqn(fqn: String): Optional<CodeSymbol>

    @Query("SELECT s FROM CodeSymbol s WHERE s.module.project.id = :projectId")
    fun findByProjectId(projectId: String): List<CodeSymbol>
}

@Repository
interface CodeRelationRepository : JpaRepository<CodeRelation, String> {
    fun findBySourceSymbolId(sourceSymbolId: String): List<CodeRelation>
    fun findByTargetSymbolId(targetSymbolId: String): List<CodeRelation>
    fun findByRelationType(relationType: RelationType): List<CodeRelation>
}

@Repository
interface EvidenceRepository : JpaRepository<EvidenceEntity, String> {
    fun findByProjectId(projectId: String): List<EvidenceEntity>
    fun findByProjectIdAndAssertionType(projectId: String, assertionType: String): List<EvidenceEntity>
    fun findByProjectIdAndAssertionKey(projectId: String, assertionKey: String): List<EvidenceEntity>
}

@Repository
interface DocumentationJobRepository : JpaRepository<DocumentationJob, String> {
    fun findByProjectId(projectId: String): List<DocumentationJob>
    fun findByStatus(status: JobStatus): List<DocumentationJob>
}
