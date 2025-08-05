package com.example.gitserver.module.user.interfaces.rest

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
import com.example.gitserver.module.user.interfaces.dto.RefreshRequest
import com.example.gitserver.module.user.interfaces.dto.UserResponse
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val ipAddress = httpServletRequest.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()
            ?: httpServletRequest.remoteAddr
        val userAgent = httpServletRequest.getHeader("User-Agent")

        val (accessToken, refreshToken, user) = authCommandService.login(
            LoginUserCommand(request.email, request.password),
            ipAddress,
            userAgent
        )
        val response = LoginResponse(accessToken, refreshToken, UserResponse.from(user))
        return ResponseEntity.ok(success(200, "로그인 성공", response))
    }

    /**
     * 토큰 재발급 API입니다.
     * 기존의 accessToken과 refreshToken을 사용하여 새로운 토큰을 발급합니다.
     */
    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val userId = jwtProvider.getUserId(request.accessToken)
        val email = jwtProvider.getEmail(request.accessToken)
        val (newAccessToken, newRefreshToken) = authQueryService.refresh(userId, email, request.refreshToken)
        val response = LoginResponse(newAccessToken, newRefreshToken)
        return ResponseEntity.ok(success(200, "토큰 재발급 성공", response))
    }

    /**
     * 로그아웃 API입니다.
     * 사용자가 로그아웃할 때 호출되어, 해당 사용자의 세션을 종료합니다.
     */
    @Operation(summary = "Logout User")
    @PostMapping("/logout")
    fun logout(
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<String>> {
        val accessToken = jwtProvider.resolveToken(request)
        val userId = jwtProvider.getUserId(accessToken)
        authCommandService.logout(userId)
        return ResponseEntity.ok(success(200, "로그아웃 성공"))
    }

}