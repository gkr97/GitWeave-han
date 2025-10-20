package com.example.gitserver.module.user.infrastructure.email

import com.example.gitserver.common.util.TokenUtils.generateVerificationToken
import com.example.gitserver.module.user.domain.EmailVerification
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.EmailVerificationException
import com.example.gitserver.module.user.infrastructure.persistence.EmailVerificationRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.sqs.EmailVerificationProducer
import com.example.gitserver.module.user.interfaces.dto.EmailVerificationMessage
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

@Service
class EmailVerifcationService(
    private val userRepository: UserRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val emailVerificationProducer: EmailVerificationProducer,
    @Value("\${app.frontend.base-url}") private val frontendBaseUrl: String,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun sendVerificationEmail(user: User) {
        val token = generateVerificationToken()

        val emailVerification = EmailVerification(
            user = user,
            token = token,
            expiresAt = LocalDateTime.now().plusHours(24)
        )
        emailVerificationRepository.save(emailVerification)

        val verificationUrl = UriComponentsBuilder
            .fromHttpUrl(frontendBaseUrl)
            .path("/email-verify")
            .queryParam("token", token)
            .build()
            .toUriString()

        val subject = "[GitServer] 이메일 인증 안내"
        val body = """
            안녕하세요, GitServer 입니다.
            
            아래 링크를 클릭해 이메일 인증을 완료해주세요.
            $verificationUrl
            
            유효기간: 24시간
        """.trimIndent()

        val message = EmailVerificationMessage(
            userId = user.id,
            email = user.email,
            subject = subject,
            body = body,
            token = token
        )
        emailVerificationProducer.sendVerificationMailMessage(message)

        log.info { "이메일 인증 메일 발송 완료: user=${user.email}, token=$token" }
    }

    @Transactional
    fun verifyToken(token: String) {
        val verification = emailVerificationRepository.findByToken(token)
            ?: throw EmailVerificationException(code = "INVALID_TOKEN", message = "잘못된 토큰입니다.")

        if (verification.isUsed) {
            throw EmailVerificationException(code = "TOKEN_ALREADY_USED", message = "이미 사용된 토큰입니다.")
        }
        if (verification.isExpired()) {
            throw EmailVerificationException(code = "TOKEN_EXPIRED", message = "만료된 토큰입니다.")
        }

        val user = verification.user
        user.verifyEmail()
        verification.markUsed()

        userRepository.save(user)
        emailVerificationRepository.save(verification)
        log.info { "이메일 인증 성공: user=${user.email}, token=$token" }
    }
}
