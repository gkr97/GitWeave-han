package com.example.gitserver.module.common.repository

import com.example.gitserver.module.common.domain.CommonCode
import org.springframework.data.jpa.repository.JpaRepository

interface CommonCodeRepository: JpaRepository<CommonCode, Long> {
    fun findByCode(code: String): CommonCode?
}