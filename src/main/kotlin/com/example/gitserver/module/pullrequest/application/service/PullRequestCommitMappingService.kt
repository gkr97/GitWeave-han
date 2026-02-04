package com.example.gitserver.module.pullrequest.application.service

import com.example.gitserver.module.gitindex.shared.domain.port.GitLogPort
import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitPathResolver
import com.example.gitserver.module.pullrequest.domain.PullRequestCommit
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestCommitRepository
import com.example.gitserver.module.pullrequest.infrastructure.persistence.PullRequestRepository
import com.example.gitserver.module.pullrequest.exception.PullRequestNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PullRequestCommitMappingService(
    private val prRepository: PullRequestRepository,
    private val commitRepo: PullRequestCommitRepository,
    private val gitLogPort: GitLogPort,
    private val gitPathResolver: GitPathResolver
) {
    /**
     * base..head 커밋 해시를 Git에서 계산해 DB 매핑 테이블을 재구성
     */
    @Transactional
    fun refresh(prId: Long, repoId: Long, base: String, head: String) {
        val pr = prRepository.findById(prId).orElseThrow { PullRequestNotFoundException(prId) }
        require(pr.repository.id == repoId) { "PR이 저장소에 속하지 않습니다." }

        val bare = gitPathResolver.bareDir(pr.repository.owner.id, pr.repository.name)
        val hashes = gitLogPort.listCommitsBetween(bare, base, head)

        commitRepo.deleteByPullRequestId(prId)
        if (hashes.isEmpty()) return
        val entities = hashes.mapIndexed { idx, h ->
            PullRequestCommit(
                pullRequest = pr,
                commitHash = h,
                seq = idx
            )
        }
        commitRepo.saveAll(entities)
    }

    /**
     * 커밋 해시 목록 조회.
     * 순서를 보장하려면 Git 로그 순서를 사용한다.
     */
    @Transactional(readOnly = true)
    fun listHashes(prId: Long): List<String> {
        val pr = prRepository.findById(prId).orElseThrow { PullRequestNotFoundException(prId) }
        val bare = gitPathResolver.bareDir(pr.repository.owner.id, pr.repository.name)
        val base = pr.baseCommitHash ?: return emptyList()
        val head = pr.headCommitHash ?: return emptyList()

        val mapped = commitRepo.findHashesByPullRequestId(prId).toSet()
        return gitLogPort.listCommitsBetween(bare, base, head).filter { it in mapped }
    }
}
