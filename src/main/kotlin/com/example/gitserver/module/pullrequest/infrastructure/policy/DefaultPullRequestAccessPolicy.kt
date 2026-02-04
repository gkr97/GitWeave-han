package com.example.gitserver.module.pullrequest.infrastructure.policy

import com.example.gitserver.module.pullrequest.domain.policy.PullRequestAccessPolicy
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Component

@Component
class DefaultPullRequestAccessPolicy(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository
) : PullRequestAccessPolicy {

    override fun canAct(repoId: Long, userId: Long, authorId: Long?): Boolean {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId) ?: return false
        val owner = repo.owner.id == userId
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
        val authorOk = authorId?.let { it == userId } ?: false
        return owner || collab || authorOk
    }

    override fun canMaintain(repoId: Long, userId: Long): Boolean {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(repoId) ?: return false
        val owner = repo.owner.id == userId
        val collab = collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
        return owner || collab
    }
}
