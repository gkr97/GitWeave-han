package com.example.gitserver.common.util

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * 메일 전송 유틸리티 클래스
 */
@Component
class MailUtils(
    private val mailSender: JavaMailSender
) {
    fun sendEmail(to: String, subject: String, body: String) {
        val message = SimpleMailMessage()
        message.setTo(to)
        message.subject = subject
        message.text = body
        mailSender.send(message)
    }
}