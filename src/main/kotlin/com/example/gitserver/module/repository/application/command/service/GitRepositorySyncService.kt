package com.example.gitserver.module.repository.application.command.service

import com.example.gitserver.module.repository.domain.Branch
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.BranchRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GitRepositorySyncService(
    private val repositoryRepository: RepositoryRepository,
    private val branchRepository: BranchRepository,
) {

    @Transactional
    fun syncBranch(repositoryId: Long, branchName: String, newHeadCommit: String?, creatorUser: User) {
        val repo = repositoryRepository.findById(repositoryId).orElseThrow {
            RepositoryNotFoundException(repositoryId)
        }
        val branch = branchRepository.findByRepositoryIdAndName(repositoryId, branchName)

        if (newHeadCommit == null) {
            branch?.let { branchRepository.delete(it) }
        } else {
            if (branch == null) {
                val existsDefault = branchRepository.existsByRepositoryIdAndIsDefaultIsTrue(repositoryId)
                branchRepository.save(
                    Branch(
                        repository = repo,
                        name = branchName,
                        headCommitHash = newHeadCommit,
                        isDefault = !existsDefault,
                        creator = creatorUser
                    )
                )
            } else {
                branch.headCommitHash = newHeadCommit
                branchRepository.save(branch)
            }
        }
    }
}
