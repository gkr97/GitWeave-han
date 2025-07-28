package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.module.repository.application.service.RepositoryAccessService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private val log = mu.KotlinLogging.logger {}

@RestController
class GitController(
    private val repoAccessService: RepositoryAccessService,
    @Value("\${git.http-backend-path}") private val gitHttpBackendPath: String,
    @Value("\${git.project-root-path}") private val gitProjectRootPath: String,
) {

    /**
     * Git HTTP 요청을 처리합니다.
     */
    @Operation(summary = "Get all users from Git")
    @RequestMapping("/{ownerId}/{repo}.git/**", method = [RequestMethod.GET, RequestMethod.POST])
    fun handleGit(
        @PathVariable ownerId: Long,
        @PathVariable repo: String,
        @RequestHeader(value = "Authorization", required = false) authorization: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        log.info("Git HTTP Request: method={}, uri={}, repo={}, authHeader={}", request.method, request.requestURI, repo, authorization != null)

        val uriMatcher = Regex("^/([0-9]+)/([\\w.-]+)\\.git").find(request.requestURI)
        val repoName = uriMatcher?.groups?.get(2)?.value

        if (repoName == null) {
            log.warn("Invalid repository path: uri={}", request.requestURI)
            response.sendError(400, "Invalid repository path")
            return
        }

        val access = repoAccessService.checkAccess(repoName, ownerId, authorization)
        log.info("Access check result: repoName={}, ownerId={}, result={}", repoName, ownerId, access)

        when (access) {
            is RepositoryAccessService.AccessResult.Authorized -> {
                log.info("Authorized. Start git-http-backend for repo {}/{}", ownerId, repoName)
                val env = buildGitBackendEnv(
                    request, ownerId, repoName, access.userId
                )
                try {
                    val processBuilder = ProcessBuilder(gitHttpBackendPath)
                    processBuilder.environment().putAll(env)
                    processBuilder.redirectErrorStream(true)

                    val process = processBuilder.start()
                    request.inputStream.copyTo(process.outputStream)
                    process.outputStream.close()

                    val output = process.inputStream.buffered()
                    val reader = output.reader(Charsets.ISO_8859_1).buffered()
                    val servletOut = response.outputStream

                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val sep = line.indexOf(':')
                        if (sep > 0) {
                            val key = line.substring(0, sep).trim()
                            val value = line.substring(sep + 1).trim()
                            headers[key] = value
                            response.setHeader(key, value)
                        }
                    }

                    headers["Status"]?.let { statusLine ->
                        val statusCode = statusLine.split(' ').firstOrNull()?.toIntOrNull()
                        if (statusCode != null) response.status = statusCode
                    }

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (output.read(buffer).also { bytesRead = it } != -1) {
                        servletOut.write(buffer, 0, bytesRead)
                    }
                    servletOut.flush()

                    process.waitFor()
                    log.info("git-http-backend finished with exitValue={}", process.exitValue())
                } catch (e: Exception) {
                    log.error("Error running git-http-backend", e)
                    response.sendError(500, "Internal Server Error")
                }
            }
            RepositoryAccessService.AccessResult.NotFound -> {
                log.warn("Repository not found: {}/{}", ownerId, repoName)
                response.sendError(404)
            }
            RepositoryAccessService.AccessResult.Unauthorized -> {
                log.warn("Unauthorized access attempt for repo: {}/{}", ownerId, repoName)
                response.sendError(401)
            }
            RepositoryAccessService.AccessResult.Forbidden -> {
                log.warn("Forbidden access: {}/{}", ownerId, repoName)
                response.sendError(403)
            }
        }
    }

    private fun buildGitBackendEnv(
        request: HttpServletRequest,
        ownerId: Long,
        repoName: String,
        userId: Long?
    ): Map<String, String> {
        val env = mutableMapOf<String, String>()
        env["GIT_PROJECT_ROOT"] = gitProjectRootPath
        env["PATH_INFO"] = "/$ownerId/$repoName.git" + request.requestURI.substringAfter("$repoName.git", "")
        env["REQUEST_METHOD"] = request.method
        env["QUERY_STRING"] = request.queryString ?: ""
        env["CONTENT_TYPE"] = request.contentType ?: ""
        env["REMOTE_USER"] = userId?.toString() ?: ""
        env["REMOTE_ADDR"] = request.remoteAddr
        env["AUTH_TYPE"] = "Basic"
        log.info("GIT_PROJECT_ROOT={}, PATH_INFO={}, REQUEST_METHOD={}, QUERY_STRING={}",
            env["GIT_PROJECT_ROOT"], env["PATH_INFO"], env["REQUEST_METHOD"], env["QUERY_STRING"])
        return env
    }
}
