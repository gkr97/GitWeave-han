package com.example.gitserver.module.gitindex.application.merge

import com.example.gitserver.module.gitindex.domain.vo.MergeType
import com.example.gitserver.module.gitindex.exception.GitMergeConflictException
import com.example.gitserver.module.gitindex.exception.GitMergeFailedException
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.springframework.stereotype.Component

/**
 * 병합 전략 - 리베이스
 */
@Component
class RebaseMergeStrategy : MergeStrategy {
    override val type = MergeType.REBASE

    override fun execute(ctx: MergeContext) {
        val git = ctx.git

        git.checkout().setName(ctx.sourceShort).call()

        val targetCommitId = git.repository.exactRef(ctx.targetFull)?.objectId
            ?: throw GitMergeFailedException(ctx.sourceFull, ctx.targetFull, "Target ref not found")

        val result = git.rebase()
            .setUpstream(targetCommitId)
            .setOperation(RebaseCommand.Operation.BEGIN)
            .call()

        when (result.status) {
            RebaseResult.Status.OK,
            RebaseResult.Status.FAST_FORWARD -> { /* ok */ }
            RebaseResult.Status.STOPPED,
            RebaseResult.Status.CONFLICTS -> {
                runCatching { git.rebase().setOperation(RebaseCommand.Operation.ABORT).call() }
                throw GitMergeConflictException(ctx.sourceFull, ctx.targetFull, "Rebase conflicts")
            }
            else -> throw GitMergeFailedException(ctx.sourceFull, ctx.targetFull, "Rebase ${result.status}")
        }

        git.checkout().setName(ctx.targetShort).call()
        git.merge()
            .include(git.repository.findRef(ctx.sourceFull))
            .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
            .call()
    }
}
