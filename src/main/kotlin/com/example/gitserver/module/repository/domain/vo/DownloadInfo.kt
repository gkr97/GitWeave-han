package com.example.gitserver.module.repository.domain.vo

import java.io.InputStream

data class DownloadInfo(
    val filename: String,
    val streamSupplier: () -> Pair<InputStream, Process>
)
