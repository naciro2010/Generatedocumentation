package io.docgen.core.model

/**
 * Enums and simple data models (non-JPA)
 * Used for file-based storage and API requests
 */

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

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
