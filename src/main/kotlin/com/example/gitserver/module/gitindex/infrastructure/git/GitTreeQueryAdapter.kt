package com.example.gitserver.module.gitindex.infrastructure.git

import com.example.gitserver.module.gitindex.domain.dto.TreeItem
import com.example.gitserver.module.gitindex.domain.port.TreeQueryRepository
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository as SpringRepository
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@SpringRepository
@Primary
class GitTreeQueryAdapter(
    private val gitPathResolver: GitPathResolver,
    private val repositoryRepository: RepositoryRepository,
) : TreeQueryRepository {

    override fun getFileTreeAtRoot(
        repositoryId: Long,
        commitHash: String,
        branch: String?
    ): List<TreeNodeResponse> {
        val ctx = resolveContext(repositoryId)
        openRepo(ctx).use { repo ->
            val tree = resolveTree(repo, commitHash)
            return walkTree(repo, tree, basePath = null)
                .map { it.toResponse(commitHash, committer = null) }
        }
    }

    override fun getFileTree(
        repositoryId: Long,
        commitHash: String,
        path: String?,
        branch: String?
    ): List<TreeNodeResponse> {
        val ctx = resolveContext(repositoryId)
        openRepo(ctx).use { repo ->
            val tree = resolveTree(repo, commitHash)
            val base = path?.takeIf { it.isNotBlank() }
            return walkTree(repo, tree, base)
                .map { it.toResponse(commitHash, committer = null) }
        }
    }

    override fun getTreeItem(
        repositoryId: Long,
        commitHash: String,
        path: String
    ): TreeItem? {
        val ctx = resolveContext(repositoryId)
        openRepo(ctx).use { repo ->
            val tree = resolveTree(repo, commitHash)
            val (oid, isDir) = findEntry(repo, tree, path) ?: return null
            return TreeItem(
                path = path,
                fileHash = if (isDir) null else oid.name
            )
        }
    }


    private data class RepoCtx(val ownerId: Long, val repoName: String)

    private fun resolveContext(repositoryId: Long): RepoCtx {
        val repo = repositoryRepository.findByIdWithOwner(repositoryId)
            ?: throw RepositoryNotFoundException(repositoryId)
        return RepoCtx(repo.owner.id, repo.name)
    }

    private fun openRepo(ctx: RepoCtx): Repository {
        val dir = File(gitPathResolver.bareDir(ctx.ownerId, ctx.repoName))
        require(dir.exists()) { "bare repo not found: $dir" }
        return FileRepositoryBuilder().setGitDir(dir).setMustExist(true).build()
    }

    private fun resolveTree(repo: Repository, commitHash: String): RevTree =
        RevWalk(repo).use { rw ->
            val oid = ObjectId.fromString(commitHash)
            val commit = rw.parseCommit(oid)
            rw.parseTree(commit.tree.id)
        }

    private data class Entry(val name: String, val path: String, val oid: ObjectId, val isDir: Boolean, val size: Long)

    private fun walkTree(repo: Repository, root: RevTree, basePath: String?): List<Entry> {
        TreeWalk(repo).use { tw ->
            tw.addTree(root)
            tw.isRecursive = false

            if (!basePath.isNullOrBlank()) {
                while (tw.next()) {
                    if (tw.isSubtree && tw.pathString == basePath) {
                        tw.enterSubtree()
                        break
                    }
                }
            }

            val out = mutableListOf<Entry>()
            while (tw.next()) {
                val name = tw.nameString
                val path = tw.pathString
                val isDir = tw.isSubtree
                val oid = tw.getObjectId(0)
                val size = if (isDir) 0 else repo.open(oid).size
                out += Entry(name, path, oid, isDir, size)
            }
            return out
        }
    }

    private fun findEntry(repo: Repository, root: RevTree, targetPath: String): Pair<ObjectId, Boolean>? {
        TreeWalk(repo).use { tw ->
            tw.addTree(root)
            tw.isRecursive = false
            val segments = targetPath.split('/').filter { it.isNotBlank() }
            var idx = 0
            while (tw.next()) {
                if (tw.nameString != segments[idx]) continue
                val isLast = (idx == segments.lastIndex)

                if (tw.isSubtree) {
                    if (isLast) return tw.getObjectId(0) to true
                    tw.enterSubtree(); idx++
                } else {
                    return if (isLast) tw.getObjectId(0) to false else null
                }
            }
        }
        return null
    }

    private fun Entry.toResponse(
        commitHash: String,
        committer: RepositoryUserResponse?
    ): TreeNodeResponse =
        TreeNodeResponse(
            name = this.name,
            path = this.path,
            isDirectory = this.isDir,
            size = if (this.isDir) null else this.size,
            lastCommitHash = commitHash,
            lastCommitMessage = null,
            lastCommittedAt = Instant.EPOCH.atOffset(ZoneOffset.UTC).toString(),
            lastCommitter = committer
        )
}
