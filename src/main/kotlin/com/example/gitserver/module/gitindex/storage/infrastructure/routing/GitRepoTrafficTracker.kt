package com.example.gitserver.module.gitindex.storage.infrastructure.routing

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitRepoTrafficTracker(
    @Value("\${git.replication.priority.traffic-window-ms:60000}") private val windowMs: Long
) {
    private val counts = ConcurrentHashMap<Long, AtomicLong>()

    fun record(repoId: Long) {
        counts.computeIfAbsent(repoId) { AtomicLong(0) }.incrementAndGet()
    }

    fun getAndReset(repoId: Long): Long {
        return counts[repoId]?.get() ?: 0L
    }

    fun snapshotAndResetAll(): Map<Long, Long> {
        val snapshot = HashMap<Long, Long>(counts.size)
        counts.forEach { (repoId, counter) ->
            val value = counter.getAndSet(0)
            if (value > 0) snapshot[repoId] = value
        }
        return snapshot
    }

    @Scheduled(fixedDelayString = "\${git.replication.priority.traffic-window-ms:60000}")
    fun decayAll() {
        counts.clear()
    }
}
