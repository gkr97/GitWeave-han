package com.example.gitserver.module.gitindex.domain.service.impl

import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.springframework.stereotype.Service
import java.io.File
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jgit.transport.*
import org.springframework.beans.factory.annotation.Value
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

@Service
class JGitRepoService(
    @Value("\${git.storage.bare-path}") private val barePath: String,
) {

    /**
     * 지정된 소유자 ID와 레포지토리 이름으로 JGit Repository를 엽니다.
     * @param ownerId 소유자 ID
     * @param repo 레포지토리 이름
     * @return JGit Repository 객체
     */
    fun openRepository(ownerId: Long, repo: String): Repository {
        val gitDir = File("$barePath/$ownerId/$repo.git")
        log.info { "[openRepository] barePath=$barePath, ownerId=$ownerId, repo=$repo" }
        if (!gitDir.exists()) {
            log.warn { "[openRepository] Repository not found: $gitDir" }
            throw IllegalArgumentException("Repository not found")
        }
        return FileRepositoryBuilder().setGitDir(gitDir).build()
    }

    /**
     * 지정된 레포지토리의 서비스에 대한 광고를 전송합니다.
     * @param service 서비스 이름 (예: git-upload-pack, git-receive-pack)
     * @param repository JGit Repository 객체
     * @param response HTTP 응답 객체
     */
    fun advertiseRefs(service: String, repository: Repository, response: HttpServletResponse) {
        response.contentType = "application/x-$service-advertisement"
        response.setHeader("Cache-Control", "no-cache")
        val serviceLine = "# service=$service\n"
        val lineLen = serviceLine.toByteArray(Charsets.UTF_8).size + 4
        val pktLine = "%04x".format(lineLen).toByteArray(Charsets.US_ASCII)
        response.outputStream.write(pktLine)
        response.outputStream.write(serviceLine.toByteArray(Charsets.UTF_8))
        response.outputStream.write("0000".toByteArray(Charsets.US_ASCII))
        when (service) {
            "git-upload-pack" -> {
                val up = UploadPack(repository)
                up.sendAdvertisedRefs(RefAdvertiser.PacketLineOutRefAdvertiser(PacketLineOut(response.outputStream)))
            }
            "git-receive-pack" -> {
                val rp = ReceivePack(repository)
                rp.sendAdvertisedRefs(RefAdvertiser.PacketLineOutRefAdvertiser(PacketLineOut(response.outputStream)))
            }
            else -> response.sendError(400, "Unsupported service: $service")
        }
    }


    /**
     * 레포지토리에 대한 upload-pack 요청을 처리합니다.
     * @param repository JGit Repository 객체
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     */
    fun uploadPack(repository: Repository, request: HttpServletRequest, response: HttpServletResponse) {
        log.info { "[uploadPack] repository=${repository.directory}" }
        response.contentType = "application/x-git-upload-pack-result"
        try {
            val up = UploadPack(repository)
            // smart protocol 설정
            up.isBiDirectionalPipe = false
            up.upload(request.inputStream, response.outputStream, null)
            log.info { "[uploadPack] upload finished" }
        } catch (e: Exception) {
            log.error(e) { "[uploadPack] Error during upload-pack" }
            response.sendError(500, "upload-pack error: ${e.message}")
        }
    }

    /**
     * 레포지토리에 대한 receive-pack 요청을 처리합니다.
     * @param repository JGit Repository 객체
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param postReceive PostReceiveHook 후크
     */
    fun receivePack(
        repository: Repository,
        request: HttpServletRequest,
        response: HttpServletResponse,
        postReceive: PostReceiveHook
    ) {
        log.info { "[receivePack] repository=${repository.directory}" }
        response.contentType = "application/x-git-receive-pack-result"
        try {
            val rp = ReceivePack(repository)
            rp.isBiDirectionalPipe = false
            rp.postReceiveHook = postReceive
            rp.receive(request.inputStream, response.outputStream, null)
            log.info { "[receivePack] receive finished" }
        } catch (e: Exception) {
            log.error(e) { "[receivePack] Error during receive-pack" }
            response.sendError(500, "receive-pack error: ${e.message}")
        }
    }
}
