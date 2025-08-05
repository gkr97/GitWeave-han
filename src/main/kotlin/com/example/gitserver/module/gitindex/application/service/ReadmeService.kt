package com.example.gitserver.module.gitindex.application.service

import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse

interface ReadmeService {
    fun getReadmeInfo(repoId: Long, commitHash: String): ReadmeResponse
    fun getReadmeContent(repoId: Long, commitHash: String): String?
    fun getReadmeHtml(repoId: Long, commitHash: String): String?
    fun getLanguageStats(repositoryId: Long): List<LanguageStatResponse>
}