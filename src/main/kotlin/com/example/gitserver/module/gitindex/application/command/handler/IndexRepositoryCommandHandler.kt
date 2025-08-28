package com.example.gitserver.module.gitindex.application.command.handler

import com.example.gitserver.module.gitindex.application.command.IndexRepositoryCommand
import com.example.gitserver.module.gitindex.application.command.IndexRepositoryHeadCommand
import com.example.gitserver.module.gitindex.application.command.IndexRepositoryPushCommand
import com.example.gitserver.module.gitindex.application.internal.ScanDiff
import com.example.gitserver.module.gitindex.application.internal.TreeScanIndexer
import com.example.gitserver.module.gitindex.domain.Commit
import com.example.gitserver.module.gitindex.domain.event.GitEvent
import com.example.gitserver.module.gitindex.domain.event.IndexingFailurePublisher
import com.example.gitserver.module.gitindex.domain.port.CommitQueryRepository
import com.example.gitserver.module.gitindex.domain.port.IndexTxRepository
import com.example.gitserver.module.gitindex.domain.vo.CommitHash
import com.example.gitserver.module.gitindex.domain.vo.TreeHash
import com.example.gitserver.module.gitindex.exception.GitCommitParseException
import com.example.gitserver.module.gitindex.exception.GitHeadNotFoundException
import com.example.gitserver.module.gitindex.exception.GitRepositoryOpenException
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Instant
import kotlin.system.measureTimeMillis

private val log = KotlinLogging.logger {}

@Service
class IndexRepositoryCommandHandler(
    private val commitQueryRepo: CommitQueryRepository,
    private val treeIndexer: TreeScanIndexer,
    private val diffIndexer: ScanDiff,
    private val failurePublisher: IndexingFailurePublisher,
    private val userRepository: UserRepository,
    private val txRepo: IndexTxRepository,
) {

    @Transactional
    fun handle(cmd: IndexRepositoryCommand) {
        when (cmd) {
            is IndexRepositoryHeadCommand -> {
                log.info { "[Index] HEAD 인덱싱 시작 - repo=${cmd.repositoryId}, workDir=${cmd.workDir}" }
                val took = measureTimeMillis { indexRepository(cmd.repositoryId, cmd.workDir) }
                log.info { "[Index] HEAD 인덱싱 종료 - repo=${cmd.repositoryId}, tookMs=$took" }
            }
            is IndexRepositoryPushCommand -> {
                log.info { "[Index] Push 인덱싱 시작 - repo=${cmd.event.repositoryId}, branch=${cmd.event.branch}, oldrev=${cmd.event.oldrev}, newrev=${cmd.event.newrev}, gitDir=${cmd.gitDir}" }
                val took = measureTimeMillis { indexPush(cmd.event, cmd.gitDir) }
                log.info { "[Index] Push 인덱싱 종료 - repo=${cmd.event.repositoryId}, tookMs=$took" }
            }
        }
    }

    companion object { private const val EMPTY_SHA1 = "0000000000000000000000000000000000000000" }

    private fun indexRepository(repositoryId: Long, workDir: Path) {
        val git = try { Git.open(workDir.toFile()) }
        catch (e: Exception) {
            log.error(e) { "[Index] Git 저장소 오픈 실패 - workDir=$workDir" }
            throw GitRepositoryOpenException(workDir.toString(), e)
        }

        git.use { g ->
            RevWalk(g.repository).use { revWalk ->
                val head = g.repository.resolve("HEAD") ?: run {
                    log.error { "[Index] HEAD 해석 실패 - repo=$repositoryId, workDir=$workDir" }
                    throw GitHeadNotFoundException()
                }
                val commit = try { revWalk.parseCommit(head) } catch (e: Exception) {
                    log.error(e) { "[Index] 커밋 파싱 실패 - repo=$repositoryId, head=$head" }
                    throw GitCommitParseException(e)
                }

                val fullBranch = normalizeBranch(g.repository.fullBranch)
                withMDC(repositoryId, fullBranch, commit.name) {
                    log.info { "[Index] HEAD 커밋 인덱싱 - branch=$fullBranch, commit=${commit.name}, tree=${commit.tree.id.name}" }
                    val commitVo = buildCommitVo(repositoryId, commit, fullBranch)
                    val took = measureTimeMillis {
                        val result = treeIndexer.scan(
                            repositoryId, commit.name, commit.tree.id, g.repository,
                            basePath = "", commitInstant = commit.committerIdent.whenAsInstant
                        )
                        finalizeCommit(
                            repositoryId = repositoryId,
                            commit = commit,
                            commitVo = commitVo,
                            result = result,
                            branch = fullBranch,
                            expectedOld = null
                        )
                    }
                    log.info { "[Index] HEAD 커밋 인덱싱 완료 - commit=${commit.name}, tookMs=$took" }
                }
            }
        }
    }

    private fun indexPush(event: GitEvent, gitDir: Path) {
        val git = try { Git.open(gitDir.toFile()) }
        catch (e: Exception) {
            log.error(e) { "[Index] Git 저장소 오픈 실패 - gitDir=$gitDir" }
            throw GitRepositoryOpenException(gitDir.toString(), e)
        }

        git.use { g ->
            val repo = g.repository
            val newId = event.newrev?.let { ObjectId.fromString(it) } ?: run {
                log.warn { "[Index] newrev 없음 - repo=${event.repositoryId}, branch=${event.branch}" }
                return
            }
            val oldId = event.oldrev?.takeIf { it != EMPTY_SHA1 }?.let { ObjectId.fromString(it) }
            val branch = normalizeBranch(event.branch)
            val isBranchCreate = (event.oldrev == null || event.oldrev == EMPTY_SHA1)

            log.debug { "[Index] Push 컨텍스트 - repo=${event.repositoryId}, branch=$branch, isBranchCreate=$isBranchCreate, oldId=${oldId?.name}, newId=${newId.name}" }

            if (isBranchCreate) {
                if (commitQueryRepo.existsCommit(event.repositoryId, newId.name)) {
                    log.info { "[Index] 브랜치 신규(커밋 기존) - 매핑만 저장 - commit=${newId.name}, branch=$branch" }
                    saveBranchMappingOnly(event.repositoryId, newId, repo, branch); return
                }
                val commits = collectUntilKnown(repo, newId) { commitQueryRepo.existsCommit(event.repositoryId, it) }
                log.info { "[Index] 브랜치 신규(히스토리 미존재) - 인덱싱 대상 커밋 수=${commits.size}" }
                for (c in commits.asReversed()) {
                    withMDC(event.repositoryId, branch, c.name) {
                        val vo = buildCommitVo(event.repositoryId, c, branch)
                        val took = measureTimeMillis {
                            val result = if (c.parents.isNotEmpty()) {
                                log.debug { "[Index] Diff 스캔 - parent=${c.parents.first().name}, tree=${c.tree.id.name}" }
                                diffIndexer.scan(event.repositoryId, c.name, c.parents.first().tree?.id, c.tree.id, repo, c.committerIdent.whenAsInstant)
                            } else {
                                log.debug { "[Index] Full 스캔 - initial commit, tree=${c.tree.id.name}" }
                                treeIndexer.scan(event.repositoryId, c.name, c.tree.id, repo, "", c.committerIdent.whenAsInstant)
                            }
                            finalizeCommit(event.repositoryId, c, vo, result, branch, null)
                        }
                        log.info { "[Index] 커밋 인덱싱 완료 - commit=${c.name}, tookMs=$took" }
                    }
                }
                return
            }

            if (oldId == null) {
                log.warn { "[Index] oldrev 없음(비정상) - repo=${event.repositoryId}, branch=$branch, new=${newId.name}" }
                return
            }

            val commits = revListOldToNew(repo, oldId, newId)
            log.info { "[Index] Push 범위 커밋 수=${commits.size} - from=${oldId.name} to=${newId.name}, branch=$branch" }
            for (c in commits) {
                if (commitQueryRepo.existsCommit(event.repositoryId, c.name)) {
                    log.debug { "[Index] 이미 인덱싱된 커밋 스킵 - commit=${c.name}" }
                    continue
                }
                withMDC(event.repositoryId, branch, c.name) {
                    val vo = buildCommitVo(event.repositoryId, c, branch)
                    val took = measureTimeMillis {
                        val result = if (c.parents.isNotEmpty()) {
                            log.debug { "[Index] Diff 스캔 - parent=${c.parents.first().name}, tree=${c.tree.id.name}" }
                            diffIndexer.scan(event.repositoryId, c.name, c.parents.first().tree?.id, c.tree.id, repo, c.committerIdent.whenAsInstant)
                        } else {
                            log.debug { "[Index] Full 스캔 - initial commit, tree=${c.tree.id.name}" }
                            treeIndexer.scan(event.repositoryId, c.name, c.tree.id, repo, "", c.committerIdent.whenAsInstant)
                        }
                        finalizeCommit(event.repositoryId, c, vo, result, branch, event.oldrev?.let { CommitHash(it) })
                    }
                    log.info { "[Index] 커밋 인덱싱 완료 - commit=${c.name}, tookMs=$took" }
                }
            }
        }
    }

    private fun finalizeCommit(
        repositoryId: Long,
        commit: RevCommit,
        commitVo: Commit,
        result: com.example.gitserver.module.gitindex.application.internal.IndexFailures,
        branch: String,
        expectedOld: CommitHash?
    ) {
        withMDC(repositoryId, branch, commit.name) {
            log.debug { "[Index] 트랜잭션 준비 - commit=${commit.name}, expectedOld=${expectedOld?.value}" }
            val sealMs = measureTimeMillis { txRepo.prepareCommit(commitVo) }
            log.debug { "[Index] 트랜잭션 준비 완료 - tookMs=$sealMs" }

            if (result.count == 0) {
                val casMs: Long
                val ok: Boolean
                val start = System.currentTimeMillis()
                ok = txRepo.sealCommitAndUpdateRef(repositoryId, branch, commitVo.hash, expectedOld)
                casMs = System.currentTimeMillis() - start
                if (ok) {
                    log.info { "[Index] 커밋 확정 완료 - commit=${commit.name}, casTookMs=$casMs" }
                } else {
                    log.warn { "[Index] Ref CAS 실패 - repo=$repositoryId, branch=$branch, commit=${commit.name}, casTookMs=$casMs" }
                }
            } else {
                result.details.forEach {
                    log.debug { "[Index] 파일 인덱싱 실패 - path=${it.path}, objectId=${it.objectId}, reason=${it.reason}" }
                    failurePublisher.publishFileFailure(repositoryId, commit.name, it.path, it.objectId, it.reason, it.throwable)
                }
                log.warn { "[Index] 파일 인덱싱 부분 실패 ${result.count}건 - commit=${commit.name}" }
            }
        }
    }

    private fun buildCommitVo(repositoryId: Long, commit: RevCommit, branch: String): Commit {
        val authorEmail = commit.authorIdent.emailAddress
        val author = userRepository.findByEmailAndIsDeletedFalse(authorEmail)
        if (author == null) {
            log.warn { "[Index] 작성자 이메일 매핑 실패 - email=$authorEmail, commit=${commit.name}" }
        } else {
            log.debug { "[Index] 작성자 이메일 매핑 - email=$authorEmail, userId=${author.id}" }
        }
        return Commit(
            repositoryId = repositoryId,
            hash = CommitHash(commit.name),
            message = commit.fullMessage,
            authorName = commit.authorIdent.name,
            authorId = author?.id ?: -1L,
            authorEmail = authorEmail,
            committedAt = commit.committerIdent.whenAsInstant,
            committerName = commit.committerIdent.name,
            committerEmail = commit.committerIdent.emailAddress,
            treeHash = TreeHash(commit.tree.id.name),
            parentHashes = commit.parents.map { it.name },
            createdAt = Instant.now(),
            branch = branch,
        )
    }

    /**
     * 브랜치 이름 정규화
     * - null/빈값/공백 -> refs/heads/main
     * - refs/heads/로 시작하면 그대로
     * - 그 외 -> refs/heads/{input}
     */
    private fun normalizeBranch(input: String?): String =
        if (input.isNullOrBlank()) {
            log.debug { "[Index] 브랜치 누락 - 기본 main 사용" }
            "refs/heads/main"
        } else if (input.startsWith("refs/heads/")) {
            input
        } else {
            val normalized = "refs/heads/$input"
            log.debug { "[Index] 브랜치 정규화 - input=$input, normalized=$normalized" }
            normalized
        }

    /**
     * 브랜치에 매핑만 저장 (커밋은 이미 존재하는 상태)
     */
    private fun saveBranchMappingOnly(repositoryId: Long, commitId: ObjectId, repo: Repository, branch: String) {
        RevWalk(repo).use { rw ->
            val rc = rw.parseCommit(commitId)
            withMDC(repositoryId, branch, rc.name) {
                val vo = buildCommitVo(repositoryId, rc, branch)
                val ok = txRepo.sealCommitAndUpdateRef(repositoryId, branch, vo.hash, null)
                if (ok) log.info { "[Index] 브랜치 매핑 저장 완료 - branch=$branch, commit=${rc.name}" }
                else log.warn { "[Index] 브랜치 매핑 CAS 실패 - branch=$branch, commit=${rc.name}" }
            }
        }
    }


    /**
     * start 커밋부터 거슬러 올라가면서 isKnown이 true를 반환하는 커밋을 만날 때까지의 커밋들을 반환
     * 반환되는 커밋들은 오래된 순서가 아님(RevWalk 순서)
     */
    private fun collectUntilKnown(repo: Repository, start: ObjectId, isKnown: (String) -> Boolean): List<RevCommit> {
        val acc = mutableListOf<RevCommit>()
        RevWalk(repo).use { rw ->
            rw.sort(RevSort.TOPO, true); rw.markStart(rw.parseCommit(start))
            for (c in rw) { if (isKnown(c.name)) break; acc += c }
        }
        log.debug { "[Index] collectUntilKnown 결과 - 수=${acc.size}, 시작=${start.name}" }
        return acc
    }

    /**
     * oldId(포함X) ~ newId(포함O) 범위의 커밋을 오래된 순서대로 반환
     */
    private fun revListOldToNew(repo: Repository, oldId: ObjectId, newId: ObjectId): List<RevCommit> {
        RevWalk(repo).use { rw ->
            rw.sort(RevSort.TOPO, true); rw.sort(RevSort.REVERSE, true)
            rw.markStart(rw.parseCommit(newId)); rw.markUninteresting(rw.parseCommit(oldId))
            val list = rw.toList()
            log.debug { "[Index] revListOldToNew 결과 - 수=${list.size}, old=${oldId.name}, new=${newId.name}" }
            return list
        }
    }

    /**
     * 커밋 단위 MDC 설정 유틸
     */
    private inline fun <T> withMDC(repositoryId: Long, branch: String, commit: String, block: () -> T): T {
        MDC.put("repoId", repositoryId.toString())
        MDC.put("branch", branch)
        MDC.put("commit", commit)
        return try { block() } finally {
            MDC.remove("repoId"); MDC.remove("branch"); MDC.remove("commit")
        }
    }
}
