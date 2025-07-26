package com.example.gitserver.module.common.service

import com.example.gitserver.module.common.dto.CommonCodeDetailResponse
import com.example.gitserver.module.common.repository.CommonCodeDetailRepository
import com.example.gitserver.module.common.repository.CommonCodeRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

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
