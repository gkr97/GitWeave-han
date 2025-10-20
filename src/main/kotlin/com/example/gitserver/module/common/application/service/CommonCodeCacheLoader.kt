package com.example.gitserver.module.common.application.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.infrastructure.repository.CommonCodeDetailRepository
import com.example.gitserver.module.common.infrastructure.repository.CommonCodeRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 애플리케이션 시작 시 공통 코드 정보를 Redis 캐시에 로드하는 서비스
 */
@Component
class CommonCodeCacheLoader(
    private val codeRepo: CommonCodeRepository,
    private val detailRepo: CommonCodeDetailRepository,
    private val redisService: CommonCodeRedisService,
) : ApplicationRunner {


    @Transactional(readOnly = true)
    override fun run(args: ApplicationArguments?) {
        val allGroups = codeRepo.findAll()
        allGroups.forEach { group ->
            val details = detailRepo.findByCodeGroup(group)
                .sortedBy { it.sortOrder }
                .map {
                    CommonCodeDetailResponse(
                        id = it.id,
                        code = it.code,
                        name = it.name,
                        sortOrder = it.sortOrder,
                        isActive = it.isActive
                    )
                }
            redisService.saveDetailsByCode(group.code, details)
        }
    }
}
