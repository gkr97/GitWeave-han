package com.example.gitserver.common.extension

import org.apache.tika.Tika

val tika = Tika()

fun ByteArray.isBinaryFile(threshold: Int = 8000): Boolean {
    val slice = if (this.size > threshold) this.sliceArray(0 until threshold) else this
    return slice.any { it == 0.toByte() }
}

fun ByteArray.countLines(): Int {
    return this.toString(Charsets.UTF_8)
        .split('\n')
        .size
}

fun ByteArray.detectMimeType(): String {
    return try {
        tika.detect(this)
    } catch (e: Exception) {
        "application/octet-stream"
    }
}