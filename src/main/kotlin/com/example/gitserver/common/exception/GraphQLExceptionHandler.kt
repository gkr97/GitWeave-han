package com.example.gitserver.common.exception

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolverAdapter() {

    /**
     * 비즈니스 예외를 처리하여 GraphQL 에러로 변환합니다.
     *
     * @param ex Throwable - 발생한 예외
     * @param env DataFetchingEnvironment - GraphQL 데이터 페칭 환경
     * @return GraphQLError - 변환된 GraphQL 에러
     */
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

            else -> null
        }
    }
}

enum class GraphQLCustomErrorType : ErrorClassification {
    BUSINESS
}