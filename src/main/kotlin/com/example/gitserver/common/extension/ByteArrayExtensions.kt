package com.example.gitserver.common.extension

import org.apache.tika.Tika

val tika = Tika()


/**
 * 바이트 배열이 바이너리 파일인지 여부를 판단합니다.
 *
 * @param threshold 검사할 바이트 수의 임계값 (기본값: 8000)
 * @return 바이너리 파일인 경우 true, 그렇지 않은 경우 false
 */
fun ByteArray.isBinaryFile(threshold: Int = 8000): Boolean {
    val slice = if (this.size > threshold) this.sliceArray(0 until threshold) else this
    return slice.any { it == 0.toByte() }
}

/**
 * 바이트 배열의 줄 수를 계산합니다.
 *
 * @return 줄 수
 */
fun ByteArray.countLines(): Int {
    return this.toString(Charsets.UTF_8)
        .split('\n')
        .size
}

/**
 * 바이트 배열의 MIME 타입을 감지합니다.
 *
 * @return MIME 타입 문자열
 */
fun ByteArray.detectMimeType(): String {
    return try {
        tika.detect(this)
    } catch (e: Exception) {
        "application/octet-stream"
    }
}