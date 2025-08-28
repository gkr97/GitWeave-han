package com.example.gitserver.module.gitindex.domain.policy

import java.io.InputStream

interface BlobMetaAnalyzer {
    fun analyzeSmall(all: ByteArray): Triple<String?, Boolean, Int?>
    fun analyzeLarge(ins: InputStream, size: Long, sampleBytes: Int): Triple<String?, Boolean, Int?>
}