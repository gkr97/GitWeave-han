package com.example.gitserver.module.gitindex.shared.domain.port

import java.io.InputStream
import java.time.Duration

interface BlobObjectStorage {
    fun putBytes(hash: String, bytes: ByteArray): String
    fun putStream(hash: String, input: InputStream, contentLength: Long): String
    fun readAsString(hash: String): String?
    fun presignForDownload(
        hash: String,
        downloadFileName: String,
        mimeType: String?,
        ttl: Duration = Duration.ofMinutes(10)
    ): Pair<String, String>
}
