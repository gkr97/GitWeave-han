package com.example.gitserver.module.pullrequest.domain.behavior

import com.example.gitserver.module.pullrequest.domain.*
import java.time.LocalDateTime

fun PullRequestReviewer.approve(codeBook: CodeBook, now: LocalDateTime) {
    this.statusCodeId = codeBook.prReviewStatusId(PrReviewStatus.APPROVED)
    this.reviewedAt = now
}

fun PullRequestReviewer.requestChanges(codeBook: CodeBook, now: LocalDateTime) {
    this.statusCodeId = codeBook.prReviewStatusId(PrReviewStatus.CHANGES_REQUESTED)
    this.reviewedAt = now
}

fun PullRequestReviewer.dismiss(codeBook: CodeBook, now: LocalDateTime) {
    this.statusCodeId = codeBook.prReviewStatusId(PrReviewStatus.DISMISSED)
    this.reviewedAt = now
}
