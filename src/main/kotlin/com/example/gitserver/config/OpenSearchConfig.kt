package com.example.gitserver.config

import org.apache.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.transport.Transport
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.opensearch.client.RestClient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.OpenSearchTransport
import org.opensearch.client.transport.rest_client.RestClientTransport

@Configuration
class OpenSearchConfig(
    @Value("\${OPENSEARCH_URL:http://localhost:9200}") private val osUrl: String
) {

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().registerKotlinModule()

    @Bean
    fun restClient(): RestClient {
        val uri = java.net.URI.create(osUrl)
        val host = uri.host ?: "localhost"
        val port = if (uri.port == -1) 9200 else uri.port
        val scheme = uri.scheme ?: "http"
        return RestClient.builder(HttpHost(host, port, scheme)).build()
    }

    @Bean
    fun openSearchClient(restClient: RestClient, objectMapper: ObjectMapper): OpenSearchClient {
        val transport: Transport = RestClientTransport(restClient, JacksonJsonpMapper(objectMapper))
        return OpenSearchClient(transport as OpenSearchTransport?)
    }
}
