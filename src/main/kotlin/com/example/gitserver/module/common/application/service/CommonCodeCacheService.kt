package com.example.gitserver.module.common.application.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.infrastructure.repository.CommonCodeDetailRepository
import com.example.gitserver.module.common.infrastructure.repository.CommonCodeRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommonCodeCacheService(
    private val codeRepo: CommonCodeRepository,
    private val detailRepo: CommonCodeDetailRepository,
    private val redisService: CommonCodeRedisService,
) {

    /** 코드 그룹에 해당하는 상세 코드 리스트 조회 (캐시 사용) */
    @Cacheable(cacheNames = ["commonCodes"], key = "#code")
    @Transactional(readOnly = true)
    fun getCodeDetailsOrLoad(code: String): List<CommonCodeDetailResponse> {
        return redisService.getDetailsByCode(code) ?: run {
            val group = codeRepo.findByCode(code)
                ?: throw IllegalArgumentException("코드 그룹 없음: $code")

            val details = detailRepo.findByCodeGroup(group).map {
                CommonCodeDetailResponse(
                    id = it.id,
                    code = it.code,
                    name = it.name,
                    sortOrder = it.sortOrder,
                    isActive = it.isActive
                )
            }

            redisService.saveDetailsByCode(code, details)
            details
        }
    }

    /** visibility 코드에 해당하는 ID 조회 (캐시 사용) */
    @Transactional(readOnly = true)
    fun getVisibilityCodeId(visibility: String): Long {
        val details = getCodeDetailsOrLoad("VISIBILITY")
        val codeDetail = details.find { it.code.equals(visibility, ignoreCase = true) }
            ?: throw IllegalArgumentException("존재하지 않는 visibility: $visibility")
        return codeDetail.id
    }
}
