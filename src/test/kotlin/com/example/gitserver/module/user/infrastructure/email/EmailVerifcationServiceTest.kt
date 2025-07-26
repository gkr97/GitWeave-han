package com.example.gitserver.module.user.infrastructure.email

import com.example.gitserver.common.util.MailUtils
import com.example.gitserver.module.user.domain.EmailVerification
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.infrastructure.persistence.EmailVerificationRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import com.example.gitserver.module.user.infrastructure.sqs.EmailVerificationProducer
import com.example.gitserver.module.user.interfaces.rest.dto.EmailVerificationMessage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any

class EmailVerifcationServiceTest {
    private val userRepository = mock(UserRepository::class.java)
    private val emailVerificationRepository = mock(EmailVerificationRepository::class.java)
    private val emailVerificationProducer = mock(EmailVerificationProducer::class.java)

    private val service = EmailVerifcationService(
        userRepository,
        emailVerificationRepository,
        emailVerificationProducer
    )

    @Test
    fun `이메일 인증 메일 SQS 메시지 발행`() {
        // given
        val user = User(
            id = 1L,
            email = "test@test.com",
            passwordHash = "pw",
            name = "테스트",
            isActive = true,
            isDeleted = false
        )

        `when`(emailVerificationRepository.save(any<EmailVerification>())).thenAnswer { it.getArgument<EmailVerification>(0) }

        // when
        service.sendVerificationEmail(user)

        // then
        verify(emailVerificationProducer).sendVerificationMailMessage(any<EmailVerificationMessage>())
    }
}
