package com.example.gitserver.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.ssl.SSLContexts
import org.opensearch.client.RestClient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@Configuration
class OpenSearchConfig(
    @Value("\${OPENSEARCH_URL:https://localhost:9200}") private val osUrl: String,
    @Value("\${OPENSEARCH_USERNAME:admin}") private val osUsername: String,
    @Value("\${OPENSEARCH_PASSWORD:admin}") private val osPassword: String
) {

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper().registerKotlinModule()

    @Bean(destroyMethod = "close")
    fun restClient(): RestClient {
        val uri = URI.create(osUrl)
        val host = uri.host ?: "localhost"
        val port = if (uri.port == -1) 9200 else uri.port
        val scheme = uri.scheme ?: "https"

        // Basic Auth 설정
        val credentialsProvider = BasicCredentialsProvider().apply {
            setCredentials(
                AuthScope(host, port),
                UsernamePasswordCredentials(osUsername, osPassword)
            )
        }

        return RestClient.builder(HttpHost(host, port, scheme))
            .setHttpClientConfigCallback { httpClientBuilder ->
                val sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null) { _, _ -> true } // 모든 인증서 신뢰
                    .build()

                httpClientBuilder
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .setMaxConnTotal(50)
                    .setMaxConnPerRoute(10)
                    .disableAuthCaching()
            }
            .setRequestConfigCallback {
                it.setConnectTimeout(5_000)
                    .setSocketTimeout(30_000)
            }
            .build()
    }

    @Bean
    fun openSearchClient(
        restClient: RestClient,
        objectMapper: ObjectMapper
    ): OpenSearchClient {
        val transport = RestClientTransport(
            restClient,
            JacksonJsonpMapper(objectMapper)
        )
        return OpenSearchClient(transport)
    }
}
