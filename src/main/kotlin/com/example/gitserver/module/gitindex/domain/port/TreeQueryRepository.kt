package com.example.gitserver.module.gitindex.domain.port

import com.example.gitserver.module.gitindex.domain.dto.TreeItem
import com.example.gitserver.module.repository.interfaces.dto.TreeNodeResponse

interface TreeQueryRepository {
    fun getFileTreeAtRoot(repoId: Long, commitHash: String, branch: String?): List<TreeNodeResponse>
    fun getFileTree(repoId: Long, commitHash: String, path: String?, branch: String?): List<TreeNodeResponse>
    fun getTreeItem(repoId: Long, commitHash: String, path: String): TreeItem?
}