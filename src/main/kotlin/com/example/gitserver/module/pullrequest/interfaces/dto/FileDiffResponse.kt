package com.example.gitserver.module.pullrequest.interfaces.dto

import com.example.gitserver.module.pullrequest.application.query.model.DiffChunk
import com.example.gitserver.module.pullrequest.interfaces.dto.InlineThreadSummary

data class FileDiffResponse(
    val filePath: String,
    val oldPath: String?,          
    val status: String,            
    val isBinary: Boolean,         
    val headBlobHash: String?,    
    val baseBlobHash: String?,    
    val additions: Int,            
    val deletions: Int,            
    val chunks: List<DiffChunk>,    
    val threads: List<InlineThreadSummary>,
    val truncated: Boolean = false,
)
