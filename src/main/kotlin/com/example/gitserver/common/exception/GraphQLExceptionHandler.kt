package com.example.gitserver.common.exception

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

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

            else -> null
        }
    }
}

enum class GraphQLCustomErrorType : graphql.ErrorClassification {
    BUSINESS
}