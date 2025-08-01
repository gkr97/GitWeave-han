package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.module.common.service.CommonCodeCacheService
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.RegisterUserException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*


@Service
class RegisterUserCommandHandler(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val messageSourceAccessor: MessageSourceAccessor,
    private val codeCacheService: CommonCodeCacheService,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 사용자 회원가입 처리
     * @param command 사용자 회원가입 커맨드
     * @return 등록된 사용자 엔티티
     * @throws RegisterUserException 이메일 중복 시 예외 발생
     */
    fun handle(command: RegisterUserCommand): User {
        if (userRepository.existsByEmailAndIsDeletedFalse(command.email)) {
            log.warn { "회원가입 실패 - 이미 존재하는 이메일: ${command.email}" }
            throw RegisterUserException(
                "EMAIL_ALREADY_EXISTS",
                messageSourceAccessor.getMessage("user.email.exists")
            )
        }

        val user = User(
            email = command.email,
            passwordHash = passwordEncoder.encode(command.password),
            name = command.name,
            isActive = true,
            isDeleted = false,
            providerCodeId = codeCacheService.getCodeDetailsOrLoad("PROVIDER")
                .firstOrNull { it.code == "local" }?.id ?: 1L,
            timezone = "Asia/Seoul",
        )

        try {
            userRepository.save(user)
        } catch (e: Exception) {
            log.error(e) { "회원가입 실패 - 사용자 저장 중 오류: ${command.email}" }
            throw RegisterUserException(
                "USER_SAVE_FAILED",
                messageSourceAccessor.getMessage("user.save.fail")
            )
        }

        log.info { "회원가입 성공: ${user.email}" }
        return user
    }
}
