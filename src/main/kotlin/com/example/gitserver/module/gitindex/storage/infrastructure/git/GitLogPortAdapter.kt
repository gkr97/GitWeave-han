package com.example.gitserver.module.gitindex.storage.infrastructure.git

import com.example.gitserver.module.gitindex.shared.domain.port.GitLogPort
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.stereotype.Component
import java.io.File

@Component
class GitLogPortAdapter : GitLogPort {

    override fun listCommitsBetween(bareGitPath: String, base: String, head: String): List<String> {
        val repoDir = File(bareGitPath)
        val repo = FileRepositoryBuilder()
            .setGitDir(repoDir)
            .setMustExist(true)
            .build()

        repo.use { r ->
            val headId = ObjectId.fromString(head)
            val baseId = ObjectId.fromString(base)

            RevWalk(r).use { walk ->
                val headCommit: RevCommit = walk.parseCommit(headId)
                val baseCommit: RevCommit = walk.parseCommit(baseId)

                walk.markStart(headCommit)
                walk.markUninteresting(baseCommit)

                val hashes = mutableListOf<String>()
                for (c in walk) hashes += c.name
                return hashes
            }
        }
    }
}
