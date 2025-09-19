package com.example.gitserver.module.pullrequest.interfaces.dto

import jakarta.validation.constraints.NotNull

data class AssignReviewerRequest(
    @field:NotNull val reviewerId: Long
)
