package com.example.gitserver.common.util

import java.net.URLDecoder
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

/**
 * Path Traversal 공격을 방어하기 위한 유틸리티
 */
object PathSecurityUtils {
    
    /**
     * 경로를 안전하게 정규화합니다.
     * 
     * 1. URL 디코딩을 반복하여 모든 인코딩 제거
     * 2. null byte 제거
     * 3. Path Traversal 시도 감지 및 차단
     * 4. 정규화 수행
     * 
     * @param input 원본 경로 문자열
     * @return 정규화된 안전한 경로
     * @throws IllegalArgumentException Path Traversal 시도 또는 잘못된 경로
     */
    fun sanitizePath(input: String): String {
        require(input.isNotBlank()) { "경로는 비어 있을 수 없습니다." }
        
        // 1. null byte 제거 및 기본 정리
        var cleaned = input.trim()
            .replace("\u0000", "") // null byte 제거
            .replace('\\', '/') // 윈도우 경로 구분자를 Unix 스타일로 통일
        
        // 2. URL 디코딩을 최대 10회 반복 (중첩 인코딩 방어)
        var decoded = cleaned
        var previousDecoded: String
        var decodeCount = 0
        do {
            previousDecoded = decoded
            try {
                decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8)
                decodeCount++
            } catch (e: IllegalArgumentException) {
                // 디코딩 실패 시 이전 값 사용
                break
            }
        } while (decoded != previousDecoded && decodeCount < 10)
        
        // 3. 다시 정리
        decoded = decoded.trim().trim('/')
        
        // 4. Path Traversal 패턴 감지 (다양한 형태)
        val pathTraversalPatterns = listOf(
            "..",
            "%2e%2e",
            "%2E%2E",
            "..%2f",
            "..%2F",
            "%2e%2e%2f",
            "%2E%2E%2F",
            "..%5c",
            "..%5C",
            "%2e%2e%5c",
            "%2E%2E%5C",
            "....//",
            "....\\\\",
            "..//",
            "..\\\\"
        )
        
        val lowerDecoded = decoded.lowercase()
        for (pattern in pathTraversalPatterns) {
            if (lowerDecoded.contains(pattern.lowercase())) {
                throw IllegalArgumentException("상위 경로 접근은 허용되지 않습니다: $input")
            }
        }
        
        // 5. Java Path API를 사용한 정규화
        try {
            val path = Paths.get(decoded).normalize()
            
            // 6. 절대 경로 또는 상위 디렉토리로 이동하는 경로 차단
            if (path.isAbsolute) {
                throw IllegalArgumentException("절대 경로는 허용되지 않습니다: $input")
            }
            
            // 7. 정규화 후 다시 ".."가 나타나는지 확인
            val normalized = path.toString().replace('\\', '/')
            if (normalized.contains("..") || normalized.startsWith("/")) {
                throw IllegalArgumentException("상위 경로 접근은 허용되지 않습니다: $input")
            }
            
            return normalized
            
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 경로 형식입니다: $input", e)
        }
    }
    
    /**
     * 경로가 안전한지 검증합니다 (예외를 던지지 않고 boolean 반환)
     */
    fun isPathSafe(input: String): Boolean {
        return try {
            sanitizePath(input)
            true
        } catch (e: Exception) {
            false
        }
    }
}
