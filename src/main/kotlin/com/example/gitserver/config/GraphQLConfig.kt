package com.example.gitserver.config

import graphql.scalars.ExtendedScalars
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * GraphQL 설정
 */
@Configuration
class GraphQLConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer = RuntimeWiringConfigurer {
        it.scalar(ExtendedScalars.GraphQLLong)
        it.scalar(ExtendedScalars.DateTime)
    }

}
