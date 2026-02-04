package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

@Component
class GitRoutingMetrics(
    private val registry: MeterRegistry,
    @Value("\${git.metrics.enabled:true}") private val enabled: Boolean
) {
    private val failoverCounter = registry.counter("git.routing.failover.count")
    private val pendingTasks = AtomicLong(0)
    private val lagCommitsByNode = ConcurrentHashMap<String, AtomicLong>()
    private val lagMsByNode = ConcurrentHashMap<String, AtomicLong>()

    init {
        Gauge.builder("git.replication.pending", pendingTasks) { it.get().toDouble() }
            .register(registry)
    }

    fun recordFailover() {
        if (enabled) failoverCounter.increment()
    }

    fun updatePendingTasks(count: Long) {
        if (enabled) pendingTasks.set(count)
    }

    fun recordLag(repoId: Long, nodeId: String, lagCommits: Long, lagMs: Long) {
        if (!enabled) return
        val commitsGauge = lagCommitsByNode.computeIfAbsent(nodeId) {
            val gauge = AtomicLong(0)
            Gauge.builder("git.replica.lag.commits", gauge) { it.get().toDouble() }
                .tags(Tags.of("nodeId", nodeId))
                .register(registry)
            gauge
        }
        val msGauge = lagMsByNode.computeIfAbsent(nodeId) {
            val gauge = AtomicLong(0)
            Gauge.builder("git.replica.lag.ms", gauge) { it.get().toDouble() }
                .tags(Tags.of("nodeId", nodeId))
                .register(registry)
            gauge
        }
        commitsGauge.set(lagCommits)
        msGauge.set(lagMs)
    }
}
