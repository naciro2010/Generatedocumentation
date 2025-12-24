package io.docgen.core.model

import jakarta.persistence.*
import java.time.Instant

/**
 * Entités JPA pour la persistence.
 */

@Entity
@Table(name = "projects")
data class Project(
    @Id
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "repository_url")
    val repositoryUrl: String? = null,

    @Column(name = "import_type")
    @Enumerated(EnumType.STRING)
    val importType: ImportType,

    @Column(name = "storage_path")
    val storagePath: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ProjectStatus = ProjectStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "analyzed_at")
    var analyzedAt: Instant? = null,

    @Column(columnDefinition = "jsonb")
    var fingerprint: String? = null, // JSON du ProjectFingerprint

    @Column(name = "lines_of_code")
    var linesOfCode: Long = 0,

    @Column(name = "file_count")
    var fileCount: Int = 0,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
) {
    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true)
    var modules: MutableList<Module> = mutableListOf()

    @OneToMany(mappedBy = "project", cascade = [CascadeType.ALL], orphanRemoval = true)
    var dependencies: MutableList<Dependency> = mutableListOf()
}

enum class ImportType {
    GIT, ZIP, LOCAL
}

enum class ProjectStatus {
    PENDING,
    IMPORTING,
    IMPORTED,
    ANALYZING,
    ANALYZED,
    GENERATING_DOCS,
    COMPLETED,
    FAILED
}

@Entity
@Table(name = "modules")
data class Module(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val path: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val type: ModuleType,

    @Column(columnDefinition = "jsonb")
    var metadata: String? = null
)

@Entity
@Table(name = "dependencies")
data class Dependency(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,

    @Column(nullable = false)
    val name: String,

    val version: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val type: DependencyType,

    val scope: String? = null
)

@Entity
@Table(name = "code_symbols")
data class CodeSymbol(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    val module: Module,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val fqn: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val symbolType: SymbolType,

    @Column(name = "file_path", nullable = false)
    val filePath: String,

    @Column(name = "start_line")
    val startLine: Int,

    @Column(name = "end_line")
    val endLine: Int? = null,

    @Column(columnDefinition = "text")
    val signature: String? = null,

    @Column(columnDefinition = "jsonb")
    var metadata: String? = null
)

enum class SymbolType {
    CLASS, INTERFACE, FUNCTION, METHOD, VARIABLE, CONSTANT, TYPE_ALIAS, ENUM
}

@Entity
@Table(name = "code_relations")
data class CodeRelation(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(name = "source_symbol_id", nullable = false)
    val sourceSymbolId: String,

    @Column(name = "target_symbol_id", nullable = false)
    val targetSymbolId: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val relationType: RelationType,

    @Column(columnDefinition = "jsonb")
    var metadata: String? = null
)

enum class RelationType {
    CALLS, DEPENDS, EXTENDS, IMPLEMENTS, USES, READS, WRITES, EXPOSES, VALIDATES
}

@Entity
@Table(name = "evidences")
data class EvidenceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(name = "project_id", nullable = false)
    val projectId: String,

    @Column(name = "assertion_type", nullable = false)
    val assertionType: String, // technical, functional, architecture

    @Column(name = "assertion_key", nullable = false)
    val assertionKey: String, // identifier unique de l'assertion

    @Column(name = "file_path", nullable = false)
    val filePath: String,

    @Column(name = "start_line", nullable = false)
    val startLine: Int,

    @Column(name = "end_line")
    val endLine: Int? = null,

    val symbol: String? = null,

    @Column(columnDefinition = "text")
    val snippet: String? = null,

    @Column(columnDefinition = "text")
    val context: String? = null
)

@Entity
@Table(name = "documentation_jobs")
data class DocumentationJob(
    @Id
    val id: String,

    @Column(name = "project_id", nullable = false)
    val projectId: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: JobStatus = JobStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "output_path")
    var outputPath: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
)

enum class JobStatus {
    PENDING, RUNNING, COMPLETED, FAILED
}
