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
     * 일반 검색 — 이름, 설명, 토픽, 언어에 대한 멀티매치 검색
     * fuzzy 매칭 포함 (사용자가 일부만 입력해도 검색 가능)
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 500, multiplier = 2.0)
    )
    fun searchRepositories(query: String, from: Int = 0, size: Int = 10): Pair<List<RepositoryDoc>, Long> {
        if (query.isBlank()) return Pair(emptyList(), 0L)

        val request = SearchRequest.Builder()
            .index("repositories")
            .query { q ->
                q.multiMatch { m ->
                    m.query(query)
                        .fields("name^3", "description", "topics", "language")
                        .fuzziness("AUTO")
                }
            }
            .from(from)
            .size(size)
            .build()

        return try {
            val response = osClient.search(request, RepositoryDoc::class.java)
            val hits = response.hits().hits().mapNotNull { it.source() }
            val total = response.hits().total()?.value()?.toLong() ?: 0L
            Pair(hits, total)
        } catch (e: Exception) {
            log.error(e) { "OpenSearch query failed for '$query'" }
            Pair(emptyList(), 0L)
        }
    }

    /**
     * 자동완성 (completion suggester 기반)
     * prefix 매칭 + 중복 제거
     */
    @Retryable(
        value = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 300, multiplier = 2.0)
    )
    fun suggestRepositories(prefix: String, size: Int = 5): List<String> {
        if (prefix.isBlank()) return emptyList()

        val req = SearchRequest.Builder()
            .index("repositories")
            .suggest { s ->
                s.suggesters("repo-suggest") { sugg ->
                    sugg.prefix(prefix)
                    sugg.completion { c ->
                        c.field("suggest").skipDuplicates(true).size(size)
                    }
                }
            }
            .build()

        return try {
            val res = osClient.search(req, RepositoryDoc::class.java)
            val suggestMap = res.suggest() ?: return emptyList()

            val repoSuggestsAny = suggestMap["repo-suggest"] ?: return emptyList()

            val suggestions = mutableListOf<String>()

            val iterable = when (repoSuggestsAny) {
                is Iterable<*> -> repoSuggestsAny
                is Array<*> -> repoSuggestsAny.asList()
                else -> listOf(repoSuggestsAny)
            }

            for (suggestion in iterable) {
                if (suggestion == null) continue

                val optionsObj: Any? = try {
                    val m = suggestion::class.java.methods.firstOrNull { it.name == "options" && it.parameterCount == 0 }
                    m?.invoke(suggestion)
                } catch (_: Exception) {
                    null
                } ?: run {
                    (suggestion as? Map<*, *>)?.get("options")
                }

                val optionsIterable: Iterable<*>? = when (optionsObj) {
                    is Iterable<*> -> optionsObj
                    is Array<*> -> optionsObj.asList()
                    else -> null
                } ?: continue

                if (optionsIterable != null) {
                    for (opt in optionsIterable) {
                        if (opt == null) continue

                        val textValue: String? = try {
                            val m = opt::class.java.methods.firstOrNull { it.name.equals("text", true) || it.name.equals("getText", true) && it.parameterCount == 0 }
                            if (m != null) {
                                (m.invoke(opt) as? String)
                            } else {
                                (opt as? Map<*, *>)?.get("text") as? String
                            }
                        } catch (ex: Exception) {
                            null
                        }

                        if (!textValue.isNullOrBlank()) {
                            suggestions += textValue
                        }
                    }
                }
            }

            suggestions.distinct()
        } catch (e: Exception) {
            log.error(e) { "OpenSearch suggest query failed for '$prefix'" }
            emptyList()
        }
    }
}
