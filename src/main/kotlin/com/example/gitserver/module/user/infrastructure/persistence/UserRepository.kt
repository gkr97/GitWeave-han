package com.example.gitserver.module.user.infrastructure.persistence

import com.example.gitserver.module.user.domain.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository: JpaRepository<User, Long> {
    fun existsByEmailAndIsDeletedFalse(email: String): Boolean

    fun findByEmailAndIsDeletedFalse(email: String): User?

    fun findByIdAndIsDeletedFalse(id: Long): User?

    fun findByEmailContainingOrNameContainingAndIsDeletedFalse(
        keyword1: String, keyword2: String
    ): List<User>

    fun findByNameAndIsDeletedFalse(name: String): User?

    @Query(
        """
        SELECT u
        FROM User u
        WHERE u.isDeleted = false
          AND (
               LOWER(u.name)  LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%'))
          )
        ORDER BY u.name ASC NULLS LAST, u.email ASC
        """
    )
    fun searchAllByKeyword(
        @Param("kw") keyword: String,
        pageable: Pageable
    ): List<User>
}
