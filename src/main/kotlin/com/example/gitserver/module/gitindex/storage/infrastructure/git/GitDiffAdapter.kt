package com.example.gitserver.module.gitindex.storage.infrastructure.git

import com.example.gitserver.module.gitindex.shared.domain.port.GitDiffPort
import com.example.gitserver.module.gitindex.shared.domain.vo.ChangedFile
import com.example.gitserver.module.gitindex.shared.domain.vo.FileChangeStatus
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
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

    private val log = KotlinLogging.logger {}

    override fun listChangedFiles(
        bareGitPath: String,
        baseRefOrSha: String,
        headRefOrSha: String
    ): List<ChangedFile> = openRepo(bareGitPath).use { repo ->
        log.debug { "Listing changed files in repo=$bareGitPath from $baseRefOrSha to $headRefOrSha" }
        val base = resolveCommit(repo, baseRefOrSha)
        val head = resolveCommit(repo, headRefOrSha)

        val entries = diffCommits(repo, base, head, detectRenames = true)

        entries.mapNotNull { e ->
            if (e.newMode?.bits == FileMode.GITLINK.bits || e.oldMode?.bits == FileMode.GITLINK.bits) {
                log.debug { "[PR diff] skip submodule entry: ${e.oldPath} -> ${e.newPath}" }
                return@mapNotNull null
            }
            if (e.newMode?.bits == FileMode.SYMLINK.bits || e.oldMode?.bits == FileMode.SYMLINK.bits) {
                log.debug { "[PR diff] skip symlink entry: ${e.oldPath} -> ${e.newPath}" }
                return@mapNotNull null
            }

            val status = toStatus(e)
            val isBinary = safeIsBinary(repo, e)

            val (add, del) = if (!isBinary && e.changeType != DiffEntry.ChangeType.DELETE) {
                safeCountAddDel(repo, e)
            } else 0 to 0

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
                headBlobHash = e.newId.toRealObjectIdOrNull()?.name(),
                baseBlobHash = e.oldId.toRealObjectIdOrNull()?.name()
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

        if (safeIsBinary(repo, target)) return ByteArray(0)

        return try {
            val out = ByteArrayOutputStream()
            DiffFormatter(out).use { fmt ->
                fmt.setRepository(repo)
                fmt.setDetectRenames(true)
                fmt.format(target)
            }
            out.toByteArray()
        } catch (e: Exception) {
            log.warn(e) { "[PR diff] renderPatch failed for $path" }
            ByteArray(0)
        }
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
            return df.scan(oldTree, newTree)
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

    private fun sideObjectIdForBinaryCheck(e: DiffEntry): ObjectId? =
        when (e.changeType) {
            DiffEntry.ChangeType.DELETE -> e.oldId.toRealObjectIdOrNull()
            DiffEntry.ChangeType.ADD,
            DiffEntry.ChangeType.COPY,
            DiffEntry.ChangeType.RENAME,
            DiffEntry.ChangeType.MODIFY -> e.newId.toRealObjectIdOrNull() ?: e.oldId.toRealObjectIdOrNull()
            else -> e.newId.toRealObjectIdOrNull() ?: e.oldId.toRealObjectIdOrNull()
        }

    private fun safeIsBinary(repo: Repository, e: DiffEntry): Boolean {
        val oid = sideObjectIdForBinaryCheck(e) ?: return false
        return try {
            val loader = repo.open(oid, Constants.OBJ_BLOB)
            loader.openStream().use { ins ->
                val buf = ByteArray(8192)
                val n = ins.read(buf)
                val bytes = if (n <= 0) ByteArray(0) else buf.copyOf(min(n, buf.size))
                RawText.isBinary(bytes)
            }
        } catch (ex: org.eclipse.jgit.errors.MissingObjectException) {
            log.warn(ex) { "[PR diff] missing blob ${oid.name} — treat as binary & continue" }
            true
        } catch (ex: Exception) {
            log.warn(ex) { "[PR diff] blob open failed ${oid.name} — treat as binary & continue" }
            true
        }
    }

    private fun safeCountAddDel(repo: Repository, e: DiffEntry): Pair<Int, Int> =
        try {
            DiffFormatter(DisabledOutputStream.INSTANCE).use { fmt ->
                fmt.setRepository(repo)
                fmt.setDetectRenames(true)
                val edits: EditList = fmt.toFileHeader(e).toEditList()
                var add = 0
                var del = 0
                for (ed in edits) {
                    add += (ed.endB - ed.beginB)
                    del += (ed.endA - ed.beginA)
                }
                add to del
            }
        } catch (ex: Exception) {
            log.warn(ex) { "[PR diff] countAddDel failed for ${e.changeType}:${e.oldPath}->${e.newPath}" }
            0 to 0
        }

    private fun AbbreviatedObjectId?.toRealObjectIdOrNull(): ObjectId? {
        val oid = runCatching { this?.toObjectId() }.getOrNull() ?: return null
        return if (oid == ObjectId.zeroId()) null else oid
    }
}
