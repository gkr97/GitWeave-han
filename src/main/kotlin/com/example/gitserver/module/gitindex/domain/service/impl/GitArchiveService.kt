package com.example.gitserver.module.gitindex.domain.service.impl

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Paths

@Service
class GitArchiveService(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String,
) {
    private val log = mu.KotlinLogging.logger {}

    fun createArchiveStream(ownerId: Long, repoName: String, branch: String): Pair<InputStream, Process> {
        val repoDir = Paths.get(bareRootPath, ownerId.toString(), "$repoName.git").toFile()
        log.info { "[GitArchiveService] archive 요청: repo=$repoDir, branch=$branch" }
        val pb = ProcessBuilder("git", "archive", "--format=zip", "--output=-", branch)
            .directory(repoDir)
            .redirectErrorStream(true)

        val proc = pb.start()

        Thread {
            proc.errorStream.bufferedReader().forEachLine { line ->
                log.warn { "[GitArchiveService] git archive stderr: $line" }
            }
        }.start()

        return Pair(proc.inputStream, proc)
    }
}
