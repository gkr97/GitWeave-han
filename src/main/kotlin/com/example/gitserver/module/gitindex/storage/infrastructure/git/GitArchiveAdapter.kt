package com.example.gitserver.module.gitindex.storage.infrastructure.git

import com.example.gitserver.common.util.GitRefUtils
import mu.KotlinLogging
import org.eclipse.jgit.api.ArchiveCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.archive.ZipFormat
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevObject
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Paths

@Service
class GitArchiveAdapter(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
) {
    private val log = KotlinLogging.logger {}

    init {
        runCatching { ArchiveCommand.registerFormat("zip", ZipFormat()) }
    }

    /**
     * JGit ArchiveCommand로 zip 아카이브 스트림 생성
     * - 브랜치/태그/커밋 SHA 모두 허용(단, refs 화이트리스트 통과 필요)
     * - 호출자는 반환된 InputStream 을 모두 읽은 뒤 close 해야 함
     */
    fun createArchiveStream(ownerId: Long, repoName: String, branch: String): InputStream {
        require(branch.isNotBlank()) { "branch는 비어 있을 수 없습니다." }

        val gitDir = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git").toFile()
        require(gitDir.exists() && gitDir.isDirectory) { "bare 저장소가 없습니다: $gitDir" }

        val fullRef = if (branch.startsWith("refs/")) branch else GitRefUtils.toFullRef(branch)
        require(fullRef.startsWith("refs/heads/") || fullRef.startsWith("refs/tags/")) {
            "허용되지 않은 ref: $fullRef"
        }

        val repo: Repository = FileRepositoryBuilder().setGitDir(gitDir).setMustExist(true).build()

        val pin = PipedInputStream(64 * 1024)
        val pout = PipedOutputStream(pin)

        val t = Thread({
            repo.use { r ->
                Git(r).use { g ->
                    try {
                        val treeId = resolveToTreeId(r, fullRef)
                        pout.use { out ->
                            g.archive()
                                .setTree(treeId)
                                .setFormat("zip")
                                .setOutputStream(out)
                                .call()
                        }
                        log.info { "[GitArchive] archive done: repo=$gitDir, ref=$fullRef, tree=${treeId.name()}" }
                    } catch (e: Exception) {
                        log.error(e) { "[GitArchive] archive 실패: repo=$gitDir, ref=$fullRef" }
                        runCatching { pout.close() }
                    }
                }
            }
        }, "git-archive-$ownerId/$repoName").apply { isDaemon = true }

        t.start()
        log.info { "[GitArchive] archive 시작: repo=$gitDir, ref=$fullRef" }
        return pin
    }

    /**
     * ref/sha → (Annotated Tag면 peel) → Commit/Tree 모두 수용하여 최종 TreeId로 변환
     */
    private fun resolveToTreeId(repo: Repository, refOrSha: String): ObjectId {
        val oid = repo.resolve(refOrSha) ?: error("ref를 해석할 수 없습니다: $refOrSha")
        RevWalk(repo).use { rw ->
            val any: RevObject = rw.parseAny(oid)

            val peeled = if (any is RevTag) rw.parseAny(any.getObject()) else any

            return when (peeled) {
                is org.eclipse.jgit.revwalk.RevCommit -> peeled.tree.id
                is RevTree -> peeled.id
                else -> error("지원하지 않는 객체 타입: ${peeled.javaClass.simpleName}")
            }
        }
    }
}
