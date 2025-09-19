package com.example.gitserver.module.gitindex.application.internal

import com.example.gitserver.module.gitindex.domain.BlobTree
import com.example.gitserver.module.gitindex.domain.port.TreeRepository
import com.example.gitserver.module.gitindex.domain.vo.CommitHash
import com.example.gitserver.module.gitindex.domain.vo.FilePath
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Component
import java.time.Instant

private val log = KotlinLogging.logger {}

@Component
class FullTreeSnapshotMaterializer(
    private val treeRepo: TreeRepository
) {
    /**
     * 해당 커밋의 전체 트리를 순회하며 모든 경로의 TREE 레코드를 업서트합니다.
     * 블롭 업로드는 하지 않습니다(변경 파일은 기존 Diff 경로에서 처리).
     */
    fun materialize(
        repositoryId: Long,
        commitHash: String,
        treeId: ObjectId,
        repo: Repository,
        at: Instant
    ) {
        TreeWalk(repo).use { tw ->
            tw.addTree(treeId)
            tw.isRecursive = true
            var count = 0
            while (tw.next()) {
                val path = tw.pathString
                val name = tw.nameString
                val isDir = tw.isSubtree
                val oid = tw.getObjectId(0).name
                val size = if (isDir) 0L else repo.open(tw.getObjectId(0)).size

                treeRepo.save(
                    BlobTree(
                        repositoryId = repositoryId,
                        commitHash = CommitHash(commitHash),
                        path = FilePath(path),
                        name = name,
                        isDirectory = isDir,
                        fileHash = oid,
                        size = size,
                        depth = path.count { it == '/' },
                        fileTypeCodeId = null,
                        lastModifiedAt = at
                    )
                )
                count++
            }
            log.debug { "[Snapshot] TREE materialized repo=$repositoryId commit=$commitHash count=$count" }
        }
    }
}
