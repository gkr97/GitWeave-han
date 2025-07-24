package com.example.gitserver.module.user.infrastructure.email

import com.example.gitserver.common.util.TokenUtils.generateVerificationToken
import com.example.gitserver.module.user.domain.EmailVerification
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.EmailVerificationException
import com.example.gitserver.module.user.infrastructure.persistence.EmailVerificationRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.sqs.EmailVerificationProducer
import com.example.gitserver.module.user.interfaces.rest.dto.EmailVerificationMessage
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EmailVerifcationService(
    private val userRepository: UserRepository,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val emailVerificationProducer: EmailVerificationProducer,
) {
    private val log = KotlinLogging.logger {}

    /**
     * 이메일 인증 메일 발송
     * @param user 인증 대상 사용자
     * @throws EmailVerificationException 이메일 인증 토큰 생성 실패 시 예외 발생
     */
    @Transactional
    fun sendVerificationEmail(user: User) {
        val token = generateVerificationToken()

        val emailVerification = EmailVerification(
            user = user,
            token = token,
            expiresAt = LocalDateTime.now().plusHours(24)
        )
        emailVerificationRepository.save(emailVerification)

        val subject = "[GitServer] 이메일 인증 안내"
        val verificationUrl = "https://your-frontend-url.com/email-verify?token=$token"
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

    /**
     * 이메일 인증 토큰 검증
     * @param token 이메일 인증 토큰
     * @throws EmailVerificationException 인증 실패 시 예외 발생
     */
    @Transactional
    fun verifyToken(token: String) {
        val verification = emailVerificationRepository.findByToken(token)
            ?: run {
                log.warn { "이메일 인증 실패: 존재하지 않는 토큰 [$token]" }
                throw EmailVerificationException(
                    code = "INVALID_TOKEN",
                    message = "잘못된 토큰입니다."
                )
            }

        if (verification.isUsed) {
            log.warn { "이메일 인증 실패: 이미 사용된 토큰 [$token], user=${verification.user.email}" }
            throw EmailVerificationException(
                code = "TOKEN_ALREADY_USED",
                message = "이미 사용된 토큰입니다."
            )
        }
        if (verification.isExpired()) {
            log.warn { "이메일 인증 실패: 만료된 토큰 [$token], user=${verification.user.email}" }
            throw EmailVerificationException(
                code = "TOKEN_EXPIRED",
                message = "만료된 토큰입니다."
            )
        }

        val user = verification.user
        user.verifyEmail()
        verification.markUsed()

        userRepository.save(user)
        emailVerificationRepository.save(verification)
        log.info { "이메일 인증 성공: user=${user.email}, token=$token" }
    }

}
