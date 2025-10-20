package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.common.jwt.CookieSupport
import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.common.response.ApiResponse.Companion.success
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.application.command.handler.RegisterUserCommandHandler
import com.example.gitserver.module.user.application.command.service.AuthCommandService
import com.example.gitserver.module.user.application.query.AuthQueryService
import com.example.gitserver.module.user.infrastructure.email.EmailVerifcationService
import com.example.gitserver.module.user.interfaces.dto.LoginRequest
import com.example.gitserver.module.user.interfaces.dto.LoginResponse
import com.example.gitserver.module.user.interfaces.dto.ResendVerifyRequest
import com.example.gitserver.module.user.interfaces.dto.UserResponse
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/v1/auth")
class UserAuthController(
    private val registerUserCommandHandler: RegisterUserCommandHandler,
    private val authCommandService: AuthCommandService,
    private val emailVerifcationService: EmailVerifcationService,
    private val authQueryService: AuthQueryService,
    private val jwtProvider: JwtProvider,
) {

    /**
     * 사용자 인증 관련 API를 처리하는 컨트롤러입니다.
     * 회원가입, 로그인, 이메일 인증, 토큰 갱신 등을 포함합니다.
     */
    @Operation(summary = "Register User")
    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody command: RegisterUserCommand): ResponseEntity<ApiResponse<UserResponse>> {
        val user = registerUserCommandHandler.handle(command)
        emailVerifcationService.sendVerificationEmail(user)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(success(HttpStatus.CREATED.value(), "회원가입 성공", UserResponse.from(user)))
    }

    /**
     * 이메일 인증을 위한 API입니다.
     * 사용자가 이메일 인증 링크를 클릭하면 이 엔드포인트가 호출되어 토큰을 검증합니다.
     */
    @Operation(summary = "email verification")
    @GetMapping("/email-verify")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<String>> {
        emailVerifcationService.verifyToken(token)
        return ResponseEntity.ok(success(200, "이메일 인증 완료"))
    }

    /**
     * 사용자 로그인 API입니다.
     * 이메일과 비밀번호를 사용하여 로그인하고, JWT 토큰을 발급합니다.
     */
    @Operation(summary = "Login User")
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpServletRequest: HttpServletRequest,
        httpServletResponse: HttpServletResponse
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val ipAddress = httpServletRequest.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()
            ?: httpServletRequest.remoteAddr
        val userAgent = httpServletRequest.getHeader("User-Agent")

        val (accessToken, refreshToken, user) = authCommandService.login(
            LoginUserCommand(request.email, request.password),
            ipAddress,
            userAgent
        )

        val remember = request.remember == true
        val refreshTtl = if (remember) Duration.ofDays(30) else Duration.ofDays(14)
        val accessTtl  = if (remember) Duration.ofMinutes(30) else Duration.ofMinutes(15)

        val sameSite = CookieSupport.SameSite.Lax
        val secure = false
        val domain: String? = null

        httpServletResponse.addHeader(
            "Set-Cookie",
            CookieSupport.buildHttpOnlyCookie(
                name = "ACCESS_TOKEN",
                value = accessToken,
                maxAge = accessTtl,
                sameSite = sameSite,
                secure = secure,
                path = "/",
                domain = domain
            )
        )

        httpServletResponse.addHeader(
            "Set-Cookie",
            CookieSupport.buildHttpOnlyCookie(
                name = "REFRESH_TOKEN",
                value = refreshToken,
                maxAge = refreshTtl,
                sameSite = sameSite,
                secure = secure,
                path = "/",
                domain = domain
            )
        )

        val response = LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = UserResponse.from(user)
        )
        return ResponseEntity.ok(success(200, "로그인 성공", response))
    }


    /**
     * 토큰 재발급 API입니다.
     * 기존의 accessToken과 refreshToken을 사용하여 새로운 토큰을 발급합니다.
     */
    @PostMapping("/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val refresh = request.cookies?.firstOrNull { it.name == "REFRESH_TOKEN" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Refresh token is missing"))

        val (newAccessToken, newRefreshToken) = authQueryService.refreshByRefreshToken(refresh)

        val sameSite = CookieSupport.SameSite.Lax
        val secure = true
        val domain: String? = null

        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildHttpOnlyCookie(
                name = "ACCESS_TOKEN",
                value = newAccessToken,
                maxAge = java.time.Duration.ofMinutes(15),
                sameSite = sameSite,
                secure = secure,
                domain = domain
            )
        )
        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildHttpOnlyCookie(
                name = "REFRESH_TOKEN",
                value = newRefreshToken,
                maxAge = java.time.Duration.ofDays(14),
                sameSite = sameSite,
                secure = secure,
                domain = domain
            )
        )

        val resBody = LoginResponse(accessToken = "", refreshToken = null)
        return ResponseEntity.ok(success(200, "토큰 재발급 성공", resBody))
    }

    /**
     * 로그아웃 API입니다.
     * 사용자가 로그아웃할 때 호출되어, 해당 사용자의 세션을 종료합니다.
     */
    @Operation(summary = "Logout User")
    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<String>> {
        val accessToken = request.cookies?.firstOrNull { it.name == "ACCESS_TOKEN" }?.value
        val userId = accessToken?.let { runCatching { jwtProvider.getUserId(it) }.getOrNull() }
        if (userId != null) authCommandService.logout(userId)

        val sameSite = CookieSupport.SameSite.Lax
        val secure = true
        val domain: String? = null

        response.addHeader("Set-Cookie", CookieSupport.deleteCookie("ACCESS_TOKEN", sameSite, secure, domain = domain))
        response.addHeader("Set-Cookie", CookieSupport.deleteCookie("REFRESH_TOKEN", sameSite, secure, domain = domain))

        return ResponseEntity.ok(success(200, "로그아웃 성공"))
    }

    @Operation(summary = "resend verification email")
    @PostMapping("/email-verify/resend")
    fun resendVerification(@RequestBody body: ResendVerifyRequest): ResponseEntity<ApiResponse<String>> {
        val user = authQueryService.findUserByEmail(body.email)
            ?: return ResponseEntity.ok(ApiResponse.success(200, "계정이 존재하지 않습니다(익명 처리)."))
        if (user.emailVerified) {
            return ResponseEntity.ok(ApiResponse.success(200, "이미 이메일 인증을 완료한 계정입니다."))
        }
        emailVerifcationService.sendVerificationEmail(user)
        return ResponseEntity.ok(ApiResponse.success(200, "인증 메일이 재발송되었습니다."))
    }

    @Operation(summary = "인증")
    @GetMapping("/me")
    fun me(request: HttpServletRequest): ResponseEntity<ApiResponse<UserResponse>> {
        val token = request.cookies?.firstOrNull { it.name == "ACCESS_TOKEN" }?.value
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Unauthorized"))

        val userId = runCatching { jwtProvider.getUserId(token) }.getOrNull()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Invalid token"))

        val user = authQueryService.findUserById(userId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "User not found"))

        return ResponseEntity.ok(ApiResponse.success(200, "OK", UserResponse.from(user)))
    }

}
