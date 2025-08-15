package com.example.gitserver.common.exception

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component


/**
 * GraphQL 예외 처리 핸들러
 * 비즈니스 예외를 GraphQL 에러로 변환하여 클라이언트에 전달합니다.
 */
@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        return when (ex) {
            is BusinessException -> GraphqlErrorBuilder.newError(env)
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

            else -> GraphqlErrorBuilder.newError(env)
                .message("Internal server error")
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

enum class GraphQLCustomErrorType : ErrorClassification {
    BUSINESS
}
