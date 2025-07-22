package com.example.gitserver.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient

@Configuration
class AwsConfig {
    @Bean
    fun s3Client(): S3Client = S3Client.create()

    @Bean
    fun sqsClient(): SqsClient = SqsClient.create()
} 