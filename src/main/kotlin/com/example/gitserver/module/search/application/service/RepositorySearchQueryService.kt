package com.example.gitserver.module.search.application.service

import com.example.gitserver.module.search.domain.RepositoryDoc
import mu.KotlinLogging
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchRequest
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class RepositorySearchQueryService(
    private val osClient: OpenSearchClient
) {

    /**
     * 일반 검색
     * - name 가중치 ↑
     * - fuzzy 검색 허용
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0)
    )
    fun searchRepositories(
        query: String,
        from: Int = 0,
        size: Int = 10
    ): Pair<List<RepositoryDoc>, Long> {

        if (query.isBlank()) {
            return Pair(emptyList(), 0L)
        }

        val request = SearchRequest.Builder()
            .index("repositories")
            .query { q ->
                q.multiMatch { m ->
                    m.query(query)
                        .fields(
                            "name^3",
                            "description",
                            "topics",
                            "language"
                        )
                        .fuzziness("AUTO")
                }
            }
            .from(from)
            .size(size)
            .build()

        val response = osClient.search(request, RepositoryDoc::class.java)

        val hits = response.hits()
            .hits()
            .mapNotNull { it.source() }

        val total = response.hits()
            .total()
            ?.value()
            ?.toLong()
            ?: 0L

        return Pair(hits, total)
    }

    /**
     * 자동완성 (completion suggester)
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 300, multiplier = 2.0)
    )
    fun suggestRepositories(
        prefix: String,
        size: Int = 5
    ): List<String> {

        if (prefix.isBlank()) return emptyList()

        val request = SearchRequest.Builder()
            .index("repositories")
            .suggest { s ->
                s.suggesters("repo-suggest") { sugg ->
                    sugg.prefix(prefix)
                    sugg.completion { c ->
                        c.field("suggest")
                            .skipDuplicates(true)
                            .size(size)
                    }
                }
            }
            .build()

        val response = osClient.search(request, RepositoryDoc::class.java)
        val suggestMap = response.suggest() ?: return emptyList()

        val repoSuggest = suggestMap["repo-suggest"] ?: return emptyList()

        val result = mutableListOf<String>()

        val iterable = when (repoSuggest) {
            is Iterable<*> -> repoSuggest
            is Array<*> -> repoSuggest.asList()
            else -> emptyList()
        }

        for (entry in iterable) {
            val options = try {
                entry!!::class.java.methods
                    .firstOrNull { it.name == "options" && it.parameterCount == 0 }
                    ?.invoke(entry)
            } catch (_: Exception) {
                null
            } ?: continue

            val optionsIterable = when (options) {
                is Iterable<*> -> options
                is Array<*> -> options.asList()
                else -> emptyList()
            }

            for (opt in optionsIterable) {
                val text = try {
                    opt!!::class.java.methods
                        .firstOrNull { it.name.equals("text", true) && it.parameterCount == 0 }
                        ?.invoke(opt) as? String
                } catch (_: Exception) {
                    null
                }

                if (!text.isNullOrBlank()) {
                    result += text
                }
            }
        }

        return result.distinct()
    }
}
