package com.example.gitserver.common.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component

/**
 * 레포지토리 관련 캐시 무효화기
 */
@Component
class RepoCacheEvictor(
    private val cacheManager: CacheManager
) {
    private val log = LoggerFactory.getLogger(RepoCacheEvictor::class.java)

    fun evictRepoDetailAndBranches() {
        clear("repoDetail", "repoBranches")
    }

    fun evictRepoLists() {
        clear("myRepos", "userRepos")
    }

    fun evictAllRepos() {
        clear("repoDetail", "repoBranches", "myRepos", "userRepos")
    }

    fun evictCommonCodes() {
        clear("commonCodes")
    }

    fun evictRepoDetailAndBranches(repoId: Long) {
        try {
            evictByPossibleKeys("repoDetail", repoId)
            evictByPossibleKeys("repoBranches", repoId)
        } catch (ex: Exception) {
            log.warn("evictRepoDetailAndBranches(repoId=$repoId) failed, fallback to global clear", ex)
        }
    }

    fun evictRepoLists(repoId: Long) {
        try {
            evictByPossibleKeys("myRepos", repoId)
            evictByPossibleKeys("userRepos", repoId)
        } catch (ex: Exception) {
            log.warn("evictRepoLists(repoId=$repoId) failed, fallback to global clear", ex)
        }
    }

    fun evictRepoAll(repoId: Long) {
        evictRepoDetailAndBranches(repoId)
        evictRepoLists(repoId)
    }

    fun evictRepoDetailAndBranchesOrGlobal(repoId: Long) {
        try {
            evictRepoDetailAndBranches(repoId)
        } catch (ex: AbstractMethodError) {
            evictRepoDetailAndBranches()
        } catch (ex: NoSuchMethodError) {
            evictRepoDetailAndBranches()
        } catch (ex: Exception) {
            log.warn("evictRepoDetailAndBranchesOrGlobal failed repoId=$repoId, fallback to global", ex)
            try { evictRepoDetailAndBranches() } catch (_: Exception) {}
        }
    }

    fun evictRepoListsOrGlobal(repoId: Long) {
        try {
            evictRepoLists(repoId)
        } catch (ex: AbstractMethodError) {
            evictRepoLists()
        } catch (ex: NoSuchMethodError) {
            evictRepoLists()
        } catch (ex: Exception) {
            log.warn("evictRepoListsOrGlobal failed repoId=$repoId, fallback to global", ex)
            try { evictRepoLists() } catch (_: Exception) {}
        }
    }

    fun evictRepoAllOrGlobal(repoId: Long) {
        try {
            evictRepoAll(repoId)
        } catch (ex: AbstractMethodError) {
            evictAllRepos()
        } catch (ex: NoSuchMethodError) {
            evictAllRepos()
        } catch (ex: Exception) {
            log.warn("evictRepoAllOrGlobal failed repoId=$repoId, fallback to global", ex)
            try { evictAllRepos() } catch (_: Exception) {}
        }
    }

    private fun clear(vararg names: String) {
        names.forEach { name ->
            try {
                cacheManager.getCache(name)?.clear()
            } catch (ex: Exception) {
                log.warn("Failed to clear cache name=$name", ex)
            }
        }
    }

    private fun evictByPossibleKeys(cacheName: String, repoId: Long) {
        val cache = cacheManager.getCache(cacheName)
        if (cache == null) {
            log.debug("No cache found for name=$cacheName")
            return
        }

        val tried = mutableListOf<Any>()
        try {
            tried.add(repoId)
            cache.evict(repoId)

            val pattern1 = "repo:$repoId"
            tried.add(pattern1)
            cache.evict(pattern1)

            val pattern2 = repoId.toString()
            tried.add(pattern2)
            cache.evict(pattern2)
        } catch (ex: Exception) {
            log.warn("evictByPossibleKeys failed cache=$cacheName repoId=$repoId tried=$tried", ex)
            throw ex
        }
    }
}
