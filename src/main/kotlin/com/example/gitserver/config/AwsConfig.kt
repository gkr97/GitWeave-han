package com.example.gitserver.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.SqsClientBuilder
import java.net.URI

@Configuration
class AwsConfig(
    @Value("\${cloud.aws.s3.endpoint}") private val s3Endpoint: String,
    @Value("\${cloud.aws.sqs.endpoint}") private val sqsEndpoint: String,
    @Value("\${cloud.aws.region.static}") private val region: String,
    @Value("\${cloud.aws.credentials.access-key}") private val accessKey: String,
    @Value("\${cloud.aws.credentials.secret-key}") private val secretKey: String,
) {

    @Bean
    fun sqsClient(): SqsClient = SqsClient.builder()
        .endpointOverride(URI.create(sqsEndpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        .build()

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .endpointOverride(URI.create(s3Endpoint))
        .forcePathStyle(true)
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        )
        .build()


}
