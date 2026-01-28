package com.example.gitserver.module.user.interfaces.graphql

import com.example.gitserver.module.repository.interfaces.dto.RepositoryUserResponse
import com.example.gitserver.module.user.application.query.UserQueryService
import com.example.gitserver.module.user.domain.CustomUserDetails
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
class UserQueryResolver(
    private val userQueryService: UserQueryService
) {

    @QueryMapping
    fun userByNickname(@Argument nickname: String): RepositoryUserResponse? {
        val user = userQueryService.findByNickname(nickname) ?: return null
        return RepositoryUserResponse(
            userId = user.id,
            nickname = user.name ?: "unKnown",
            profileImageUrl = user.profileImageUrl
        )
    }
}
