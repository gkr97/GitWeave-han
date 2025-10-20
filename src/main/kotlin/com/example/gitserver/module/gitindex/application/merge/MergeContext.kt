package com.example.gitserver.module.gitindex.application.merge

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent

data class MergeContext(
    val git: Git,
    val sourceFull: String,
    val targetFull: String,
    val sourceShort: String,
    val targetShort: String,
    val authorIdent: PersonIdent,
    val message: String?
)