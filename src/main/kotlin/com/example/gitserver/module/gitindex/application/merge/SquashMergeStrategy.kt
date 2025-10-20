package com.example.gitserver.module.gitindex.application.merge

import com.example.gitserver.module.gitindex.domain.vo.MergeType
import com.example.gitserver.module.gitindex.exception.GitMergeConflictException
import com.example.gitserver.module.gitindex.exception.GitMergeFailedException
import org.eclipse.jgit.api.MergeResult
import org.springframework.stereotype.Component

/**
 * 병합 전략 - 스쿼시 병합
 */
@Component
class SquashMergeStrategy : MergeStrategy {
    override val type = MergeType.SQUASH

    override fun execute(ctx: MergeContext) {
        val git = ctx.git
        val srcId = git.repository.exactRef(ctx.sourceFull)?.objectId
            ?: throw GitMergeFailedException(ctx.sourceFull, ctx.targetFull, "Source ref not found")

        val result = git.merge()
            .include(srcId)
            .setSquash(true)
            .setCommit(false)
            .call()

        if (!result.mergeStatus.isSuccessful) {
            if (result.mergeStatus == MergeResult.MergeStatus.CONFLICTING) {
                throw GitMergeConflictException(ctx.sourceFull, ctx.targetFull, result.conflicts?.keys?.joinToString())
            }
            throw GitMergeFailedException(ctx.sourceFull, ctx.targetFull, result.mergeStatus.name)
        }

        git.commit()
            .setAuthor(ctx.authorIdent)
            .setCommitter(ctx.authorIdent)
            .setMessage(ctx.message ?: "Squash merge ${ctx.sourceShort} into ${ctx.targetShort}")
            .call()
    }
}
