package com.example.gitserver.module.gitindex.indexer.application

import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationDlqEntity
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationDlqRepository
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationExecutor
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationNotifier
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationTaskEntity
import com.example.gitserver.module.gitindex.indexer.infrastructure.routing.GitReplicationTaskRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.GitNodeRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.persistence.RepoLocationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import org.springframework.context.annotation.Profile

@Service
@Profile("gitstorage")
class ReplicationService(
    private val taskRepository: GitReplicationTaskRepository,
    private val repoLocationRepository: RepoLocationRepository,
    private val gitNodeRepository: GitNodeRepository,
    private val executor: GitReplicationExecutor,
    private val dlqRepository: GitReplicationDlqRepository,
    private val notifier: GitReplicationNotifier,
    @Value("\${git.replication.max-attempts:3}") private val maxAttempts: Int,
    @Value("\${git.replication.rebuild-on-failure:false}") private val rebuildOnFailure: Boolean,
    @Value("\${git.replication.dlq-enabled:true}") private val dlqEnabled: Boolean
) {
    /**
     * 주어진 작업 ID에 대해 작업을 획득합니다.
     *
     * @param taskId 작업 ID
     * @return 작업 획득 성공 여부
     */
    fun claimTask(taskId: Long): Boolean =
        taskRepository.claimTask(taskId, "PENDING", "RUNNING") == 1

    /**
     * 주어진 복제 작업을 처리합니다.
     *
     * @param task 복제 작업 엔티티
     */
    fun processTask(task: GitReplicationTaskEntity) {
        task.status = "RUNNING"
        task.attempt += 1
        task.updatedAt = LocalDateTime.now()
        taskRepository.save(task)

        val location = repoLocationRepository.findById(task.repoId).orElse(null)
        val primaryNode = if (location != null) {
            gitNodeRepository.findById(location.primaryNodeId).orElse(null)
        } else null

        if (primaryNode == null) {
            failTask(task, "primary node not found")
            return
        }

        val result = executor.replicate(task, primaryNode.host, forceRebuild = false)
        if (result.success) {
            task.status = "SUCCEEDED"
            task.lastError = null
        } else {
            task.lastError = result.error
            if (task.attempt >= maxAttempts && rebuildOnFailure) {
                val rebuild = executor.replicate(task, primaryNode.host, forceRebuild = true)
                if (rebuild.success) {
                    task.status = "SUCCEEDED"
                    task.lastError = null
                } else {
                    task.status = "FAILED"
                    task.lastError = rebuild.error
                }
            } else {
                task.status = if (task.attempt >= maxAttempts) "FAILED" else "PENDING"
            }
        }
        task.updatedAt = LocalDateTime.now()
        taskRepository.save(task)

        if (task.status == "FAILED" && dlqEnabled) {
            enqueueDlq(task)
        }
    }

    private fun failTask(task: GitReplicationTaskEntity, reason: String) {
        task.lastError = reason
        task.status = if (task.attempt >= maxAttempts) "FAILED" else "PENDING"
        task.updatedAt = LocalDateTime.now()
        taskRepository.save(task)
        if (task.status == "FAILED" && dlqEnabled) {
            enqueueDlq(task)
        }
    }

    private fun enqueueDlq(task: GitReplicationTaskEntity) {
        if (dlqRepository.existsByTaskId(task.id)) return
        dlqRepository.save(
            GitReplicationDlqEntity(
                taskId = task.id,
                repoId = task.repoId,
                sourceNodeId = task.sourceNodeId,
                targetNodeId = task.targetNodeId,
                attempt = task.attempt,
                lastError = task.lastError
            )
        )
        notifier.notifyFailure(task, task.lastError)
    }
}
