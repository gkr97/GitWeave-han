package com.example.gitserver.module.repository.application.command.handler

import com.example.gitserver.module.common.cache.RepoCacheEvictor
import com.example.gitserver.module.common.cache.registerRepoCacheEvictionAfterCommit
import com.example.gitserver.module.common.cache.registerRepoCacheEvictionAfterCommitForRepo
import com.example.gitserver.module.repository.application.command.AddRepositoryStarCommand
import com.example.gitserver.module.repository.application.command.RemoveRepositoryStarCommand
import com.example.gitserver.module.repository.domain.RepositoryStar
import com.example.gitserver.module.repository.domain.event.RepositoryStarred
import com.example.gitserver.module.repository.domain.event.RepositoryUnstarred
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.exception.UserNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryStarRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import java.time.LocalDateTime

@Service
class RepositoryStarCommandHandler(
    private val repositoryRepository: RepositoryRepository,
    private val userRepository: UserRepository,
    private val starRepository: RepositoryStarRepository,
    private val events: ApplicationEventPublisher,
    private val evictor: RepoCacheEvictor
) {
    private val log = LoggerFactory.getLogger(RepositoryStarCommandHandler::class.java)

    @Transactional
    fun handle(command: AddRepositoryStarCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(command.repositoryId)
            ?: throw RepositoryNotFoundException(command.repositoryId)

        val user = userRepository.findByIdAndIsDeletedFalse(command.requesterId)
            ?: throw UserNotFoundException(command.requesterId)

        if (starRepository.existsByUserIdAndRepositoryId(user.id, repo.id)) {
            return
        }

        try {
            val star = RepositoryStar(user = user, repository = repo, createdAt = LocalDateTime.now())
            starRepository.save(star)
        } catch (ex: DataIntegrityViolationException) {
            log.debug("Repository star race: user=${user.id} repo=${repo.id}")
        }
        registerRepoCacheEvictionAfterCommitForRepo(evictor, repoId = repo.id)

        events.publishEvent(RepositoryStarred(repo.id, user.id))
    }

    @Transactional
    fun handle(command: RemoveRepositoryStarCommand) {
        val repo = repositoryRepository.findByIdAndIsDeletedFalse(command.repositoryId)
            ?: throw RepositoryNotFoundException(command.repositoryId)

        starRepository.deleteByUserIdAndRepositoryId(command.requesterId, repo.id)

        registerRepoCacheEvictionAfterCommitForRepo(evictor, repoId = repo.id)
        events.publishEvent(RepositoryUnstarred(repo.id, command.requesterId))
    }
}
