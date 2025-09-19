package com.example.gitserver.module.pullrequest.interfaces.dto

import jakarta.validation.constraints.NotNull

data class RemoveReviewerRequest(
    @field:NotNull val reviewerId: Long
)