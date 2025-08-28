package com.example.gitserver.module.gitindex.infrastructure.git

import com.example.gitserver.common.util.GitRefUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Paths

@Service
class GitArchiveAdapter(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
) {
    private val log = mu.KotlinLogging.logger {}

    /**
     * 특정 브랜치(또는 ref)에 대한 zip 아카이브 스트림 생성
     * 반환: (stdout InputStream, Process)
     */
    fun createArchiveStream(ownerId: Long, repoName: String, branch: String): Pair<InputStream, Process> {
        require(branch.isNotBlank()) { "branch는 비어 있을 수 없습니다." }

        val repoDir = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git").toFile()

        val fullRef = if (branch.startsWith("refs/")) branch else GitRefUtils.toFullRef(branch)
        val short = GitRefUtils.toShortName(fullRef) ?: branch

        log.info { "[GitArchiveService] archive 요청: repo=$repoDir, branch(full)=$fullRef, short=$short" }

        val pb = ProcessBuilder("git", "archive", "--format=zip", "--output=-", fullRef)
            .directory(repoDir)
            .redirectErrorStream(true)

        val proc = pb.start()

        return Pair(proc.inputStream, proc)
    }
}
