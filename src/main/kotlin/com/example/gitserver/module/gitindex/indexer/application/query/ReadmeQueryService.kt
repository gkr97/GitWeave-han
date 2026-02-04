package com.example.gitserver.module.gitindex.indexer.application.query

import com.example.gitserver.module.gitindex.shared.domain.port.BlobObjectStorage
import com.example.gitserver.module.gitindex.shared.domain.port.BlobQueryRepository
import com.example.gitserver.module.gitindex.storage.infrastructure.git.GitPathResolver
import com.example.gitserver.common.cache.RequestCache
import com.example.gitserver.module.repository.exception.RepositoryNotFoundException
import com.example.gitserver.module.repository.infrastructure.persistence.RepositoryRepository
import com.example.gitserver.module.repository.interfaces.dto.LanguageStatResponse
import com.example.gitserver.module.repository.interfaces.dto.ReadmeResponse
import mu.KotlinLogging
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Service
import java.io.File
import java.nio.charset.StandardCharsets

@Service
class ReadmeQueryService(
    private val blobQueryRepository: BlobQueryRepository,
    private val blobObjectStorage: BlobObjectStorage,
    private val repositoryRepository: RepositoryRepository,
    private val gitPathResolver: GitPathResolver,
    private val requestCache: RequestCache,
) {

    private val log = KotlinLogging.logger {}

    companion object {
        const val MAX_TEXT_FILE_SIZE = 1_048_576L // 1MB
        private val README_CANDIDATES = setOf(
            "readme", "readme.md", "readme.markdown", "readme.rst", "readme.txt"
        )
    }

    /**
     * README 파일의 경로/블롭해시를 JGit으로 탐색
     */
    fun getReadmeInfo(repoId: Long, commitHash: String): ReadmeResponse {
        val found = runCatching { findReadmePathAndHash(repoId, commitHash) }.getOrNull()
        val (path, blobHash) = found ?: return ReadmeResponse(
            exists = false, path = "", blobHash = null
        )
        return ReadmeResponse(exists = true, path = path, blobHash = blobHash)
    }

    /**
     * README 원문(텍스트) 반환
     * - 실패/미존재/바이너리면 null 반환
     */
    fun getReadmeContent(repoId: Long, commitHash: String): String? {
        val found = runCatching { findReadmePathAndHash(repoId, commitHash) }.getOrNull()
            ?: return null

        val (_, blobHash) = found
        val bareDir = resolveRepoContext(repoId).second

        return runCatching {
            FileRepositoryBuilder().setGitDir(File(bareDir)).setMustExist(true).build().use { repo ->
                val loader = repo.open(ObjectId.fromString(blobHash))
                val size = loader.size

                if (size <= MAX_TEXT_FILE_SIZE) {
                    val bytes = loader.bytes
                    if (!isBinary(bytes, size)) {
                        return@use bytes.toString(StandardCharsets.UTF_8)
                    }
                }

                val key = "blobs/$blobHash"
                blobObjectStorage.readAsString(key)?.let { return@use it }

                val probe = if (size <= MAX_TEXT_FILE_SIZE) loader.bytes else ByteArray(0)
                if (probe.isNotEmpty() && !isBinary(probe, size)) {
                    return@use probe.toString(StandardCharsets.UTF_8)
                }

                null
            }
        }.onFailure { e ->
            log.warn(e) { "[getReadmeContent] README 로드 실패 repo=$repoId, commit=$commitHash, blob=$blobHash" }
        }.getOrNull()
    }

    /**
     * README HTML 렌더링
     * - 원문이 없거나 렌더 실패하면 null
     */
    fun getReadmeHtml(repoId: Long, commitHash: String): String? {
        val md = getReadmeContent(repoId, commitHash) ?: return null
        return try {
            val parser = Parser.builder().build()
            val document = parser.parse(md)
            HtmlRenderer.builder().build().render(document)
        } catch (e: Exception) {
            log.warn(e) { "[getReadmeHtml] 마크다운 렌더링 실패 repo=$repoId, commit=$commitHash" }
            null
        }
    }

    /**
     * 언어 통계 (확장자 → 언어 간단 매핑)
     */
    fun getLanguageStats(repositoryId: Long): List<LanguageStatResponse> {
        val counts = runCatching { blobQueryRepository.countBlobsByExtension(repositoryId) }
            .onFailure { e -> log.warn(e) { "[getLanguageStats] 확장자 집계 실패 repo=$repositoryId" } }
            .getOrElse { emptyMap() }

        val total = counts.values.sum().takeIf { it > 0 } ?: return emptyList()

        val extToLang = mapOf(
            "kt" to "Kotlin",
            "java" to "Java",
            "md" to "Markdown",
            "sh" to "Shell",
            "py" to "Python"
        )

        return counts.mapNotNull { (ext, count) ->
            val lang = extToLang[ext] ?: return@mapNotNull null
            LanguageStatResponse(
                extension = ext,
                language = lang,
                count = count,
                ratio = count.toFloat() / total
            )
        }.sortedByDescending { it.ratio }
    }

    private fun resolveRepoContext(repoId: Long): Pair<com.example.gitserver.module.repository.domain.Repository, String> {
        val repo = runCatching { requestCache.getRepo(repoId) }.getOrNull()
            ?: repositoryRepository.findByIdWithOwner(repoId)
                ?.also { runCatching { requestCache.putRepo(it) } }
            ?: throw RepositoryNotFoundException(repoId)
        val bare = gitPathResolver.bareDir(repo.owner.id, repo.name)
        return repo to bare
    }

    /**
     * 커밋 루트에서 README 후보 파일 탐색 (상대경로/블롭해시 반환)
     */
    private fun findReadmePathAndHash(repoId: Long, commitHash: String): Pair<String, String>? {
        val (_, bareDir) = resolveRepoContext(repoId)

        FileRepositoryBuilder().setGitDir(File(bareDir)).setMustExist(true).build().use { repo ->
            val tree: RevTree = RevWalk(repo).use { rw ->
                val oid = ObjectId.fromString(commitHash)
                val commit = rw.parseCommit(oid)
                rw.parseTree(commit.tree.id)
            }

            TreeWalk(repo).use { tw ->
                tw.addTree(tree)
                tw.isRecursive = false
                while (tw.next()) {
                    if (tw.isSubtree) continue
                    if (tw.nameString.lowercase() in README_CANDIDATES) {
                        val path = tw.pathString
                        val blobHash = tw.getObjectId(0).name
                        return path to blobHash
                    }
                }
            }
        }
        return null
    }

    private fun isBinary(bytes: ByteArray, size: Long): Boolean {
        if (size > MAX_TEXT_FILE_SIZE) return true
        val probe = bytes.take(8000)
        return probe.any { it == 0.toByte() }
    }
}
