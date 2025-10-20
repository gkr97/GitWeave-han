package com.example.gitserver.module.gitindex.infrastructure.git

import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.CommitResponse
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.io.File
import java.time.ZoneOffset

@Repository
@Primary
class GitCommitQueryAdapter(
    private val gitPathResolver: GitPathResolver,
    private val repositoryRepository: RepositoryRepository,
) : CommitQueryRepository {

    override fun getLatestCommit(repositoryId: Long, branch: String): CommitResponse? {
        val repoEntity = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val full = if (branch.startsWith("refs/heads/")) branch else "refs/heads/$branch"
        val dir = File(gitPathResolver.bareDir(repoEntity.owner.id, repoEntity.name))
        FileRepositoryBuilder().setGitDir(dir).setMustExist(true).build().use { repo ->
            val ref = repo.exactRef(full) ?: return null
            RevWalk(repo).use { rw ->
                val c = rw.parseCommit(ref.objectId)
                return CommitResponse(
                    hash = c.name,
                    message = c.fullMessage ?: "(no message)",
                    committedAt = c.committerIdent.whenAsInstant.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                    author = RepositoryUserResponse(
                        userId = repoEntity.owner.id,
                        nickname = c.authorIdent.name ?: "unknown",
                        profileImageUrl = null
                    )
                )
            }
        }
    }

    override fun getCommitByHash(repositoryId: Long, commitHash: String): CommitResponse? {
        val repoEntity = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val dir = File(gitPathResolver.bareDir(repoEntity.owner.id, repoEntity.name))
        FileRepositoryBuilder().setGitDir(dir).setMustExist(true).build().use { repo ->
            val oid = ObjectId.fromString(commitHash)
            RevWalk(repo).use { rw ->
                val c = rw.parseCommit(oid)
                return CommitResponse(
                    hash = c.name,
                    message = c.fullMessage ?: "(no message)",
                    committedAt = c.committerIdent.whenAsInstant.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                    author = RepositoryUserResponse(
                        userId = repoEntity.owner.id,
                        nickname = c.authorIdent.name ?: "unknown",
                        profileImageUrl = null
                    )
                )
            }
        }
    }

    override fun existsCommit(repositoryId: Long, commitHash: String): Boolean {
        val repoEntity = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val dir = File(gitPathResolver.bareDir(repoEntity.owner.id, repoEntity.name))
        FileRepositoryBuilder().setGitDir(dir).setMustExist(true).build().use { repo ->
            return try {
                val oid = ObjectId.fromString(commitHash)
                RevWalk(repo).use { rw ->
                    rw.parseCommit(oid) != null
                }
            } catch (_: Exception) { false }
        }
    }

    override fun getLatestCommitBatch(
        repositoryId: Long,
        fullRefs: List<String>
    ): Map<String, CommitResponse?> {
        if (fullRefs.isEmpty()) return emptyMap()

        val repoEntity = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)

        val dir = File(gitPathResolver.bareDir(repoEntity.owner.id, repoEntity.name))
        FileRepositoryBuilder().setGitDir(dir).setMustExist(true).build().use { repo ->
            RevWalk(repo).use { rw ->
                val out = LinkedHashMap<String, CommitResponse?>(fullRefs.size)

                fullRefs.forEach { full ->
                    val ref = repo.exactRef(
                        if (full.startsWith("refs/")) full else "refs/heads/$full"
                    )
                    if (ref == null) {
                        out[full] = null
                    } else {
                        val c = rw.parseCommit(ref.objectId)
                        out[full] = CommitResponse(
                            hash = c.name,
                            message = c.fullMessage ?: "(no message)",
                            committedAt = c.committerIdent.whenAsInstant
                                .atOffset(ZoneOffset.UTC).toLocalDateTime(),
                            author = RepositoryUserResponse(
                                userId = repoEntity.owner.id,
                                nickname = c.authorIdent.name ?: "unknown",
                                profileImageUrl = null
                            )
                        )
                    }
                }
                return out
            }
        }
    }

}
