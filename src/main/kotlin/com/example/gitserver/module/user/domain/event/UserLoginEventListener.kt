package com.example.gitserver.module.user.domain.event

import com.example.gitserver.module.user.domain.LoginHistory
import com.example.gitserver.module.user.infrastructure.persistence.LoginHistoryRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.util.LogContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component


@Component
class UserLoginEventListener(
    private val userRepository: UserRepository,
    private val loginHistoryRepository: LoginHistoryRepository,
) {
    @EventListener
    fun onLogin(event: UserLoginEvent) {
        LogContext.with(
            "eventType" to "USER_LOGIN",
            "userId" to event.userId.toString()
        ) {
            val user = userRepository.findById(event.userId).orElseThrow()
            loginHistoryRepository.save(
                LoginHistory(
                    user = user,
                    ipAddress = event.ipAddress,
                    userAgent = event.userAgent,
                    loginAt = event.loginAt,
                    success = event.success
                )
            )
        }
    }
}
