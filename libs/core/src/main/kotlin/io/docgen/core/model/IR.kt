package io.docgen.core.model

import io.docgen.core.domain.Evidence
import io.docgen.core.domain.Assertion
import java.time.Instant

/**
 * Intermediate Representation (IR) - Modèle normalisé agnostique de la techno.
 * Tous les plugins produisent des fragments IR.
 */

data class ProjectIR(
    val projectId: String,
    val name: String,
    val fingerprint: ProjectFingerprint,
    val modules: List<ModuleIR> = emptyList(),
    val dependencies: List<DependencyIR> = emptyList(),
    val metadata: ProjectMetadata = ProjectMetadata()
)

data class ProjectFingerprint(
    val languages: Map<String, Double>, // language -> confidence score
    val frameworks: Map<String, Double>,
    val buildTools: Map<String, String>,
    val detectedFiles: List<String>
)

data class ProjectMetadata(
    val repositoryUrl: String? = null,
    val mainBranch: String? = null,
    val linesOfCode: Long = 0,
    val fileCount: Int = 0,
    val createdAt: Instant = Instant.now()
)

data class ModuleIR(
    val id: String,
    val name: String,
    val path: String,
    val type: ModuleType,
    val services: List<ServiceIR> = emptyList(),
    val entities: List<EntityIR> = emptyList(),
    val endpoints: List<EndpointIR> = emptyList(),
    val jobs: List<JobIR> = emptyList(),
    val events: List<EventIR> = emptyList(),
    val evidences: List<Evidence> = emptyList()
)

enum class ModuleType {
    API, WORKER, LIBRARY, UI, DATABASE, INFRASTRUCTURE, UNKNOWN
}

data class ServiceIR(
    val id: String,
    val name: String,
    val fqn: String, // fully qualified name
    val type: String, // class/interface/object
    val methods: List<MethodIR> = emptyList(),
    val dependencies: List<String> = emptyList(), // service IDs
    val evidences: List<Evidence> = emptyList()
)

data class MethodIR(
    val name: String,
    val signature: String,
    val visibility: String,
    val returnType: String? = null,
    val parameters: List<ParameterIR> = emptyList(),
    val calls: List<String> = emptyList(), // method FQNs called
    val evidences: List<Evidence> = emptyList()
)

data class ParameterIR(
    val name: String,
    val type: String,
    val optional: Boolean = false,
    val defaultValue: String? = null
)

data class EndpointIR(
    val id: String,
    val method: HttpMethod,
    val path: String,
    val handler: String, // FQN of handler function/method
    val requestBody: TypeReference? = null,
    val responseBody: TypeReference? = null,
    val queryParams: List<ParameterIR> = emptyList(),
    val pathParams: List<ParameterIR> = emptyList(),
    val validations: List<ValidationRule> = emptyList(),
    val evidences: List<Evidence> = emptyList()
)

enum class HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

data class TypeReference(
    val fqn: String,
    val generic: List<TypeReference> = emptyList()
)

data class ValidationRule(
    val field: String,
    val rule: String,
    val message: String? = null,
    val evidences: List<Evidence> = emptyList()
)

data class EntityIR(
    val id: String,
    val name: String,
    val fqn: String,
    val table: String? = null,
    val fields: List<FieldIR> = emptyList(),
    val relations: List<RelationIR> = emptyList(),
    val evidences: List<Evidence> = emptyList()
)

data class FieldIR(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false,
    val validations: List<ValidationRule> = emptyList()
)

data class RelationIR(
    val type: RelationType,
    val targetEntity: String,
    val fieldName: String,
    val inverseSide: String? = null
)

enum class RelationType {
    ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
}

data class JobIR(
    val id: String,
    val name: String,
    val schedule: String? = null, // cron or description
    val handler: String, // FQN
    val triggerType: JobTriggerType,
    val evidences: List<Evidence> = emptyList()
)

enum class JobTriggerType {
    SCHEDULED, EVENT_DRIVEN, MANUAL
}

data class EventIR(
    val id: String,
    val name: String,
    val type: EventType,
    val payload: TypeReference? = null,
    val publishers: List<String> = emptyList(), // service FQNs
    val subscribers: List<String> = emptyList(),
    val evidences: List<Evidence> = emptyList()
)

enum class EventType {
    DOMAIN_EVENT, INTEGRATION_EVENT, SYSTEM_EVENT
}

data class DependencyIR(
    val name: String,
    val version: String? = null,
    val type: DependencyType,
    val scope: String? = null,
    val evidences: List<Evidence> = emptyList()
)

enum class DependencyType {
    LIBRARY, FRAMEWORK, DATABASE, MESSAGE_BROKER, EXTERNAL_SERVICE
}
