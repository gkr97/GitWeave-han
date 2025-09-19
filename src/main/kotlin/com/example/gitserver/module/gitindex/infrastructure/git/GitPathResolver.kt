package com.example.gitserver.module.gitindex.infrastructure.git

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Paths

@Component
class GitPathResolver(
    @Value("\${git.storage.bare-path}") private val bareRootPath: String
) {
    fun bareDir(ownerId: Long, repoName: String): String =
        Paths.get(bareRootPath, ownerId.toString(), "$repoName.git").toAbsolutePath().toString()
}
