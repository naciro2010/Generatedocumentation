package io.docgen.core.domain

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Représente une preuve pointant vers du code source.
 * Toute assertion doit être backing par des evidences.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Evidence(
    val filePath: String,
    val startLine: Int,
    val endLine: Int? = null,
    val symbol: String? = null,
    val snippet: String? = null,
    val context: String? = null
) {
    fun toReference(): String = buildString {
        append(filePath)
        append(":")
        append(startLine)
        if (endLine != null && endLine != startLine) {
            append("-")
            append(endLine)
        }
        if (symbol != null) {
            append(" (")
            append(symbol)
            append(")")
        }
    }
}

enum class Confidence {
    CERTAIN,     // Preuve directe dans le code
    PROBABLE,    // Déduction forte (ex: test, doc)
    HYPOTHESE,   // Inférence faible
    INCERTAIN    // Pas de preuve
}

/**
 * Assertion avec niveau de confiance et preuves.
 */
data class Assertion<T>(
    val value: T,
    val confidence: Confidence,
    val evidences: List<Evidence> = emptyList(),
    val reasoning: String? = null,
    val uncertainties: List<String> = emptyList()
) {
    companion object {
        fun <T> certain(value: T, vararg evidences: Evidence): Assertion<T> =
            Assertion(value, Confidence.CERTAIN, evidences.toList())

        fun <T> probable(value: T, reasoning: String, vararg evidences: Evidence): Assertion<T> =
            Assertion(value, Confidence.PROBABLE, evidences.toList(), reasoning)

        fun <T> hypothesis(value: T, reasoning: String, uncertainties: List<String> = emptyList()): Assertion<T> =
            Assertion(value, Confidence.HYPOTHESE, emptyList(), reasoning, uncertainties)

        fun <T> uncertain(value: T, uncertainties: List<String>): Assertion<T> =
            Assertion(value, Confidence.INCERTAIN, emptyList(), null, uncertainties)
    }
}
