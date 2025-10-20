package com.example.gitserver.module.common.infrastructure.repository

import com.example.gitserver.module.common.domain.CommonCode
import com.example.gitserver.module.common.domain.CommonCodeDetail
import org.springframework.data.jpa.repository.JpaRepository

interface CommonCodeDetailRepository : JpaRepository<CommonCodeDetail, Long> {

    fun findByCodeGroup(codeGroup: CommonCode): List<CommonCodeDetail>
}