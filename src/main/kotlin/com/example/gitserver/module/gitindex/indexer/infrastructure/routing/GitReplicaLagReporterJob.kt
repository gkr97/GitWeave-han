package com.example.gitserver.module.gitindex.indexer.infrastructure.routing

import com.example.gitserver.module.gitindex.indexer.application.LagReportService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile

@Component
@Profile("gitstorage")
class GitReplicaLagReporterJob(
    private val lagReportService: LagReportService,
    @Value("\${git.routing.lag-report.enabled:false}") private val enabled: Boolean
) {
    @Scheduled(fixedDelayString = "\${git.routing.lag-report-interval-ms:10000}")
    fun reportLag() {
        if (!enabled) return
        lagReportService.reportAll()
    }
}
