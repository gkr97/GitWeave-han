package com.example.gitserver.module.common.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.repository.CommonCodeDetailRepository
import com.example.gitserver.module.common.repository.CommonCodeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommonCodeCacheService(
    private val codeRepo: CommonCodeRepository,
    private val detailRepo: CommonCodeDetailRepository,
    private val redisService: CommonCodeRedisService,
) {
    @Transactional
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

}