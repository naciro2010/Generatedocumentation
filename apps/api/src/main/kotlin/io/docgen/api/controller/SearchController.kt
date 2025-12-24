package io.docgen.api.controller

import io.docgen.core.search.SearchResult
import io.docgen.core.search.SemanticSearchService
import kotlinx.coroutines.runBlocking
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/search")
class SearchController(
    private val semanticSearchService: SemanticSearchService
) {

    @GetMapping("/{projectId}")
    fun search(
        @PathVariable projectId: String,
        @RequestParam query: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): SearchResponse = runBlocking {
        val results = semanticSearchService.search(projectId, query, limit)

        SearchResponse(
            query = query,
            results = results.map { result ->
                SearchResultDTO(
                    symbolName = result.symbol.name,
                    symbolType = result.symbol.symbolType.name,
                    filePath = result.symbol.filePath,
                    line = result.symbol.startLine,
                    similarity = result.similarity,
                    rank = result.rank
                )
            }
        )
    }
}

data class SearchResponse(
    val query: String,
    val results: List<SearchResultDTO>
)

data class SearchResultDTO(
    val symbolName: String,
    val symbolType: String,
    val filePath: String,
    val line: Int,
    val similarity: Double,
    val rank: Int
)
