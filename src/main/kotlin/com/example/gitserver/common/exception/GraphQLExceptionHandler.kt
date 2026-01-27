package com.example.gitserver.common.exception

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import mu.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component



/**
 * GraphQL 예외 처리 핸들러
 * 비즈니스 예외를 GraphQL 에러로 변환하여 클라이언트에 전달합니다.
 * 민감한 정보 노출을 최소화합니다.
 */
@Component
class GraphQLExceptionHandler(
    private val environment: Environment
) : DataFetcherExceptionResolverAdapter() {

    private val logger = KotlinLogging.logger {}
    
    private val isDevelopment: Boolean
        get() = environment.activeProfiles.contains("dev")

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        
        return when (ex) {
            is BusinessException -> {
                logger.warn { "GraphQL BusinessException: code=${ex.code}" }
                GraphqlErrorBuilder.newError(env)
                    .message(ex.message ?: "Business Error")
                    .errorType(GraphQLCustomErrorType.BUSINESS)
                    .extensions(
                        mapOf(
                            "code" to ex.code,
                            "status" to ex.status.value(),
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    .build()
            }

            else -> {
                logger.error(
                    "GraphQL Unhandled Exception: ${ex.javaClass.simpleName}",
                    ex
                )
                
                val errorMessage = if (isDevelopment) {
                    ex.message ?: "Internal server error"
                } else {
                    "Internal server error. Please contact support if the problem persists."
                }
                
                GraphqlErrorBuilder.newError(env)
                    .message(errorMessage)
                    .errorType(GraphQLCustomErrorType.BUSINESS)
                    .extensions(
                        mapOf(
                            "code" to "INTERNAL_ERROR",
                            "status" to 500,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    .build()
            }
        }
    }
}

enum class GraphQLCustomErrorType : ErrorClassification {
    BUSINESS
}
