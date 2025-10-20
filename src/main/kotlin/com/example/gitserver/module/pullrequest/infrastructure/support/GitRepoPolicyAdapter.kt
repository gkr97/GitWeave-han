package com.example.gitserver.module.pullrequest.infrastructure.support

import com.example.gitserver.common.util.GitRefUtils
import com.example.gitserver.module.gitindex.domain.port.GitRepositoryPort
import com.example.gitserver.module.pullrequest.domain.GitRepoPolicy
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Component

@Component
class GitRepoPolicyAdapter(
    private val repoRepo: RepositoryRepository,
    private val collabRepo: CollaboratorRepository,
    private val branchRepo: BranchRepository,
    private val git: GitRepositoryPort
) : GitRepoPolicy {

    /**
     * 사용자가 해당 저장소의 소유자이거나 협력자인지 여부를 반환합니다.
     */
    override fun isCollaboratorOrOwner(repositoryId: Long, userId: Long): Boolean {
        val repo = repoRepo.findByIdAndIsDeletedFalse(repositoryId) ?: return false
        return (repo.owner.id == userId) || collabRepo.existsByRepositoryIdAndUserId(repositoryId, userId)
    }

    /**
     * 지정된 브랜치가 저장소에 존재하는지 여부를 반환합니다.
     */
    override fun branchExists(repositoryId: Long, fullRef: String): Boolean {
        val ref = GitRefUtils.toFullRef(fullRef)
        return branchRepo.findByRepositoryIdAndName(repositoryId, ref) != null
    }

    /**
     * 지정된 참조의 최신 커밋 해시를 반환합니다.
     */
    override fun getHeadCommitHash(repositoryId: Long, fullRef: String): String {
        val repo = repoRepo.findByIdAndIsDeletedFalse(repositoryId) ?: error("repo not found: $repositoryId")
        val ref = GitRefUtils.toFullRef(fullRef)
        return git.getHeadCommitHash(repo, ref)
    }
}
