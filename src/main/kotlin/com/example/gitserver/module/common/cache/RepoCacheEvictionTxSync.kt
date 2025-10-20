package com.example.gitserver.module.common.cache

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val log = LoggerFactory.getLogger("RepoCacheEviction")

/**
 * 트랜잭션 커밋 후에 레포지토리 캐시를 무효화하는 TransactionSynchronization 구현체
 */
class RepoCacheEvictionTxSync(
    private val evictor: RepoCacheEvictor,
    private val repoId: Long? = null,
    private val evictDetailAndBranches: Boolean = false,
    private val evictLists: Boolean = false,
    private val evictAll: Boolean = false,
) : TransactionSynchronization {

    override fun afterCommit() {
        try {
            if (repoId != null) {
                when {
                    evictAll -> evictor.evictAllRepos()
                    evictDetailAndBranches && evictLists -> {
                        evictor.evictRepoDetailAndBranchesOrGlobal(repoId)
                        evictor.evictRepoListsOrGlobal(repoId)
                    }
                    evictDetailAndBranches -> evictor.evictRepoDetailAndBranchesOrGlobal(repoId)
                    evictLists -> evictor.evictRepoListsOrGlobal(repoId)
                }
            } else {
                when {
                    evictAll -> evictor.evictAllRepos()
                    evictDetailAndBranches && evictLists -> {
                        evictor.evictRepoDetailAndBranches()
                        evictor.evictRepoLists()
                    }
                    evictDetailAndBranches -> evictor.evictRepoDetailAndBranches()
                    evictLists -> evictor.evictRepoLists()
                }
            }
        } catch (ex: Exception) {
            log.warn("RepoCacheEvictionTxSync.afterCommit failed repoId=$repoId", ex)
        }
    }
}

/**
 * 트랜잭션 커밋 후에 레포지토리 캐시를 무효화하도록 등록합니다.
 *
 * @param evictor 레포지토리 캐시 무효화기
 * @param evictDetailAndBranches 상세 및 브랜치 캐시 무효화 여부
 * @param evictLists 리스트 캐시 무효화 여부
 * @param evictAll 모든 캐시 무효화 여부
 */
fun registerRepoCacheEvictionAfterCommit(
    evictor: RepoCacheEvictor,
    evictDetailAndBranches: Boolean = false,
    evictLists: Boolean = false,
    evictAll: Boolean = false,
) {
    try {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                RepoCacheEvictionTxSync(evictor, null, evictDetailAndBranches, evictLists, evictAll)
            )
        } else {
            when {
                evictAll -> evictor.evictAllRepos()
                evictDetailAndBranches && evictLists -> {
                    evictor.evictRepoDetailAndBranches()
                    evictor.evictRepoLists()
                }
                evictDetailAndBranches -> evictor.evictRepoDetailAndBranches()
                evictLists -> evictor.evictRepoLists()
            }
        }
    } catch (ex: Exception) {
        log.warn("registerRepoCacheEvictionAfterCommit failed (global)", ex)
    }
}
/**
 * 트랜잭션 커밋 후에 특정 레포지토리의 캐시를 무효화하도록 등록합니다.
 *
 * @param evictor 레포지토리 캐시 무효화기
 * @param repoId 레포지토리 ID
 * @param evictDetailAndBranches 상세 및 브랜치 캐시 무효화 여부
 * @param evictLists 리스트 캐시 무효화 여부
 * @param evictAll 모든 캐시 무효화 여부
 */
fun registerRepoCacheEvictionAfterCommitForRepo(
    evictor: RepoCacheEvictor,
    repoId: Long,
    evictDetailAndBranches: Boolean = false,
    evictLists: Boolean = false,
    evictAll: Boolean = false,
) {
    try {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                RepoCacheEvictionTxSync(evictor, repoId, evictDetailAndBranches, evictLists, evictAll)
            )
        } else {
            when {
                evictAll -> evictor.evictAllRepos()
                evictDetailAndBranches && evictLists -> {
                    evictor.evictRepoDetailAndBranchesOrGlobal(repoId)
                    evictor.evictRepoListsOrGlobal(repoId)
                }
                evictDetailAndBranches -> evictor.evictRepoDetailAndBranchesOrGlobal(repoId)
                evictLists -> evictor.evictRepoListsOrGlobal(repoId)
            }
        }
    } catch (ex: Exception) {
        log.warn("registerRepoCacheEvictionAfterCommitForRepo failed repoId=$repoId", ex)
    }
}
