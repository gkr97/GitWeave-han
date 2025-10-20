package com.example.gitserver.module.pullrequest.domain.behavior

import com.example.gitserver.module.pullrequest.domain.*

fun PullRequest.assertOpen(codeBook: CodeBook) {
    val openId = codeBook.prStatusId(PrStatus.OPEN)
    if (this.statusCodeId != openId) throw IllegalStateException("Pull request is not open")
}