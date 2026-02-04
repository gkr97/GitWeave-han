package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.exception.UserLoginException
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.domain.event.UserLoginEvent
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.common.util.LogContext
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import java.time.LocalDateTime


@Service
class LoginUserCommandHandler(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val messageAccessor: MessageSourceAccessor,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = KotlinLogging.logger {}
    /**
     * 사용자 로그인 처리 eventPublisher: ApplicationEventPublisher,
     * @param command 사용자 로그인 커맨드
     * @return 로그인된 사용자 엔티티
     * @throws UserLoginException 이메일 또는 비밀번호가 잘못된 경우
     */
    fun handle(command: LoginUserCommand, ipAddress: String, userAgent: String?): User {
        val user = userRepository.findByEmailAndIsDeletedFalse(command.email)
            ?: run {
                log.warn { "로그인 실패 - 존재하지 않는 이메일: ${command.email}" }
                throw UserLoginException(
                    code = "USER_NOT_FOUND",
                    message = messageAccessor.getMessage("user.login.fail.email"),
                    status = HttpStatus.UNAUTHORIZED
                )
            }

        if (!passwordEncoder.matches(command.password, user.passwordHash)) {
            log.warn { "로그인 실패 - 비밀번호 불일치: ${command.email}" }
            throw UserLoginException(
                code = "INVALID_PASSWORD",
                message = messageAccessor.getMessage("user.login.fail.password"),
                status = HttpStatus.UNAUTHORIZED
            )
        }

        if (!user.emailVerified) {
            log.warn { "로그인 실패 - 이메일 미인증: ${command.email}" }
            throw UserLoginException(
                code = "EMAIL_NOT_VERIFIED",
                message = messageAccessor.getMessage("user.email.not-verified"),
                status = HttpStatus.FORBIDDEN
            )
        }

        log.info { "로그인 성공: ${command.email}" }

        LogContext.with(
            "eventType" to "USER_LOGIN",
            "userId" to user.id.toString()
        ) {
            eventPublisher.publishEvent(
                UserLoginEvent(
                    userId = user.id,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    loginAt = LocalDateTime.now(),
                    success = true
                )
            )
        }

        return user
    }
}
