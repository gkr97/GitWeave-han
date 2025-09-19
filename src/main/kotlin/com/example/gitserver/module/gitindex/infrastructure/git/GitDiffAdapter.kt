package com.example.gitserver.module.gitindex.infrastructure.git

import com.example.gitserver.module.gitindex.domain.port.GitDiffPort
import com.example.gitserver.module.gitindex.domain.vo.ChangedFile
import com.example.gitserver.module.gitindex.domain.vo.FileChangeStatus
import org.eclipse.jgit.diff.*
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

@Service
class GitDiffAdapter : GitDiffPort {

    private val log = mu.KotlinLogging.logger {}

    override fun listChangedFiles(
        bareGitPath: String,
        baseRefOrSha: String,
        headRefOrSha: String
    ): List<ChangedFile> = openRepo(bareGitPath).use { repo ->
        log.debug { "Listing changed files in repo=$bareGitPath from $baseRefOrSha to $headRefOrSha" }
        val base = resolveCommit(repo, baseRefOrSha)
        val head = resolveCommit(repo, headRefOrSha)

        val entries = diffCommits(repo, base, head, detectRenames = true)


        entries.map { e ->
            val status = toStatus(e)
            val isBinary = isBinary(repo, e)
            val (add, del) = if (!isBinary) countAddDel(repo, e) else 0 to 0

            ChangedFile(
                path = when (e.changeType) {
                    DiffEntry.ChangeType.DELETE -> e.oldPath
                    else -> e.newPath
                },
                oldPath = when (e.changeType) {
                    DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY -> e.oldPath
                    else -> null
                },
                status = status,
                isBinary = isBinary,
                additions = add,
                deletions = del,
                headBlobHash = e.newId.toObjectIdOrNull()?.name(),
                baseBlobHash = e.oldId.toObjectIdOrNull()?.name()
            )
        }
    }

    override fun renderPatch(
        bareGitPath: String,
        baseRefOrSha: String,
        headRefOrSha: String,
        path: String
    ): ByteArray = openRepo(bareGitPath).use { repo ->
        val base = resolveCommit(repo, baseRefOrSha)
        val head = resolveCommit(repo, headRefOrSha)
        val diffs = diffCommits(repo, base, head, detectRenames = true)

        val target = diffs.firstOrNull { e ->
            when (e.changeType) {
                DiffEntry.ChangeType.DELETE -> e.oldPath == path
                else -> e.newPath == path
            }
        } ?: return ByteArray(0)

        if (isBinary(repo, target)) return ByteArray(0)

        val out = ByteArrayOutputStream()
        DiffFormatter(out).use { fmt ->
            fmt.setRepository(repo)
            fmt.setDetectRenames(true)
            fmt.format(target)
        }
        out.toByteArray()
    }

    override fun resolveCommitHash(bareGitPath: String, refOrSha: String): String =
        openRepo(bareGitPath).use { repo ->
            repo.resolve(refOrSha)?.name
                ?: error("해당 참조/커밋을 찾을 수 없습니다: $refOrSha")
        }

    private fun openRepo(bareGitPath: String): Repository {
        val dir = File(bareGitPath)
        require(dir.exists()) { "Git 디렉터리가 존재하지 않습니다: $bareGitPath" }
        require(dir.isDirectory) { "Git 경로가 디렉터리가 아닙니다: $bareGitPath" }
        require(File(dir, "HEAD").exists()) { "HEAD 파일이 없습니다(올바른 bare repo 아님): $bareGitPath" }

        return FileRepositoryBuilder()
            .setGitDir(dir)
            .setMustExist(true)
            .build()
    }

    private fun resolveCommit(repo: Repository, ref: String): RevCommit =
        RevWalk(repo).use { rw ->
            val oid = repo.resolve(ref) ?: error("참조/커밋 해석 실패: $ref")
            rw.parseCommit(oid)
        }

    private fun diffCommits(
        repo: Repository,
        base: RevCommit,
        head: RevCommit,
        detectRenames: Boolean
    ): List<DiffEntry> {
        val oldTree = treeIter(repo, base)
        val newTree = treeIter(repo, head)

        DiffFormatter(DisabledOutputStream.INSTANCE).use { df ->
            df.setRepository(repo)
            df.setDetectRenames(detectRenames)
            val entries = df.scan(oldTree, newTree)

            if (!detectRenames) return entries

            val rd = RenameDetector(repo)
            rd.addAll(entries)
            return rd.compute()
        }
    }

    private fun treeIter(repo: Repository, commit: RevCommit): CanonicalTreeParser =
        repo.newObjectReader().use { reader ->
            CanonicalTreeParser().apply { reset(reader, commit.tree) }
        }

    private fun toStatus(e: DiffEntry): FileChangeStatus = when (e.changeType) {
        DiffEntry.ChangeType.ADD -> FileChangeStatus.ADDED
        DiffEntry.ChangeType.MODIFY -> FileChangeStatus.MODIFIED
        DiffEntry.ChangeType.DELETE -> FileChangeStatus.DELETED
        DiffEntry.ChangeType.RENAME -> FileChangeStatus.RENAMED
        DiffEntry.ChangeType.COPY -> FileChangeStatus.COPIED
    }

    private fun isBinary(repo: Repository, e: DiffEntry): Boolean {
        val loader = e.newId.toObjectIdOrNull()?.let { repo.open(it, Constants.OBJ_BLOB) }
            ?: e.oldId.toObjectIdOrNull()?.let { repo.open(it, Constants.OBJ_BLOB) }
            ?: return false
        val bytes = loader.openStream().use { ins ->
            val buf = ByteArray(8192)
            val n = ins.read(buf)
            if (n <= 0) ByteArray(0) else buf.copyOf(min(n, buf.size))
        }
        return RawText.isBinary(bytes)
    }

    private fun countAddDel(repo: Repository, e: DiffEntry): Pair<Int, Int> =
        DiffFormatter(DisabledOutputStream.INSTANCE).use { fmt ->
            fmt.setRepository(repo)
            fmt.setDetectRenames(true)
            val edits: EditList = fmt.toFileHeader(e).toEditList()
            var add = 0
            var del = 0
            for (ed in edits) {
                add += ed.endB - ed.beginB
                del += ed.endA - ed.beginA
            }
            add to del
        }

    private fun AbbreviatedObjectId?.toObjectIdOrNull(): ObjectId? =
        runCatching { this?.toObjectId() }.getOrNull()
}
