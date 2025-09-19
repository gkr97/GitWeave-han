package com.example.gitserver.module.pullrequest.application.query.support

data class ParsedLine(
    val type: Char,           
    val oldLine: Int?,
    val newLine: Int?,
    val content: String,
    val position: Int,        
    val anchor: String = "",   
    val commentCount: Int? = 0
)