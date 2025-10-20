package com.example.gitserver.module.repository.infrastructure.policy

import com.example.gitserver.module.repository.domain.policy.RepoAccessPolicy
import com.example.gitserver.module.repository.infrastructure.persistence.CollaboratorRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import org.springframework.stereotype.Component

@Component
class DefaultRepoAccessPolicy(
    private val repositoryRepository: RepositoryRepository,
    private val collaboratorRepository: CollaboratorRepository
) : RepoAccessPolicy {

    override fun isOwner(repoId: Long, userId: Long): Boolean {
        val repo = repositoryRepository.findById(repoId).orElse(null) ?: return false
        return repo.owner.id == userId
    }

    override fun canWrite(repoId: Long, userId: Long): Boolean {
        if (isOwner(repoId, userId)) return true
        return collaboratorRepository.existsByRepositoryIdAndUserId(repoId, userId)
    }
}
