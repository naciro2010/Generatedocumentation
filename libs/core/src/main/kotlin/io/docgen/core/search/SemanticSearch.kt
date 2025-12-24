package io.docgen.core.search

import io.docgen.core.model.CodeSymbol

/**
 * Service de recherche sémantique basé sur les embeddings.
 */
interface SemanticSearchService {
    /**
     * Recherche des symboles similaires à la query.
     */
    suspend fun search(projectId: String, query: String, limit: Int = 10): List<SearchResult>

    /**
     * Indexe un symbole (génère et stocke son embedding).
     */
    suspend fun indexSymbol(symbol: CodeSymbol)

    /**
     * Indexe tous les symboles d'un projet.
     */
    suspend fun indexProject(projectId: String)
}

data class SearchResult(
    val symbol: CodeSymbol,
    val similarity: Double,
    val rank: Int
)

/**
 * Service de génération d'embeddings.
 */
interface EmbeddingService {
    /**
     * Génère un embedding pour un texte donné.
     */
    suspend fun embed(text: String): FloatArray
}

/**
 * Simple embedding basé sur TF-IDF (fallback sans LLM).
 */
class TFIDFEmbeddingService : EmbeddingService {

    override suspend fun embed(text: String): FloatArray {
        // Simplified TF-IDF embedding (768 dimensions)
        val words = text.toLowerCase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }

        val embedding = FloatArray(768) { 0f }

        // Hash-based simple embedding
        words.forEachIndexed { index, word ->
            val hash = word.hashCode()
            val dimension = Math.abs(hash) % 768
            embedding[dimension] += 1f / (index + 1)
        }

        // Normalize
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }
}

/**
 * Calcule la similarité cosinus entre deux vecteurs.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "Vectors must have same dimension" }

    var dotProduct = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    return if (normA > 0 && normB > 0) {
        dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    } else {
        0.0
    }
}
