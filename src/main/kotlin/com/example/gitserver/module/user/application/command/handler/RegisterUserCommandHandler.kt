package com.example.gitserver.module.user.application.command.handler

import com.example.gitserver.module.common.application.service.CommonCodeCacheService
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.application.service.IdenticonGenerator
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.RegisterUserException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import mu.KotlinLogging
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RegisterUserCommandHandler(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val messageSourceAccessor: MessageSourceAccessor,
    private val codeCacheService: CommonCodeCacheService,
    private val identiconGenerator: IdenticonGenerator,
    private val s3Uploader: S3Uploader
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

        val providerCodeId = codeCacheService.getCodeDetailsOrLoad("PROVIDER")
            .firstOrNull { it.code == "local" }?.id ?: 1L


        val toSave = User(
            email = command.email,
            passwordHash = passwordEncoder.encode(command.password),
            name = command.name,
            isActive = true,
            isDeleted = false,
            providerCodeId = providerCodeId,
            timezone = "Asia/Seoul",
        )

        val saved = try {
            userRepository.save(toSave)
        } catch (e: Exception) {
            log.error(e) { "회원가입 실패 - 사용자 저장 중 오류: ${command.email}" }
            throw RegisterUserException(
                "USER_SAVE_FAILED",
                messageSourceAccessor.getMessage("user.save.fail")
            )
        }

        try {
            val png = identiconGenerator.generate(
                seed = command.email,
                size = 256
            )
            val key = "user-profile-pictures/${saved.id}.png"

            val presignedUrl = s3Uploader.uploadBytesAndGetPresignedGetUrl(
                key = key,
                bytes = png,
                contentType = "image/png",
                expiry = Duration.ofDays(7)
            )

            saved.updateProfileImage(presignedUrl)
            userRepository.save(saved)
        } catch (e: Exception) {
            log.warn(e) { "identicon 생성/업로드 실패: userId=${saved.id}" }
        }

        log.info { "회원가입 성공: ${saved.email}, avatar=${saved.profileImageUrl}" }
        return saved
    }
}
