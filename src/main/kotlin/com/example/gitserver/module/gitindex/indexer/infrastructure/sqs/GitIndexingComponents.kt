package com.example.gitserver.module.gitindex.indexer.infrastructure.sqs

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("gitindexer")
class GitIndexingComponentsMarker
