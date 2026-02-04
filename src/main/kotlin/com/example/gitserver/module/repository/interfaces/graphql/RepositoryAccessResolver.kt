package com.example.gitserver.module.repository.interfaces.graphql

import com.example.gitserver.module.repository.interfaces.dto.RepoDetailResponse
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class RepositoryAccessResolver {
    @SchemaMapping(typeName = "RepositoryResponse", field = "isOwner")
    fun isOwner(repository: RepoDetailResponse): Boolean =
        repository.isOwner

    @SchemaMapping(typeName = "RepositoryResponse", field = "isStarred")
    fun isStarred(repository: RepoDetailResponse): Boolean =
        repository.isStarred

    @SchemaMapping(typeName = "RepositoryResponse", field = "isInvited")
    fun isInvited(repository: RepoDetailResponse): Boolean =
        repository.isInvited
}
