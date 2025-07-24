package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.common.response.ApiResponse
import com.example.gitserver.common.response.ApiResponse.Companion.success
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.application.command.handler.RegisterUserCommandHandler
import com.example.gitserver.module.user.application.service.AuthCommandService
import com.example.gitserver.module.user.application.service.AuthQueryService
import com.example.gitserver.module.user.infrastructure.email.EmailVerifcationService
import com.example.gitserver.module.user.interfaces.rest.dto.LoginRequest
import com.example.gitserver.module.user.interfaces.rest.dto.LoginResponse
import com.example.gitserver.module.user.interfaces.rest.dto.RefreshRequest
import com.example.gitserver.module.user.interfaces.rest.dto.UserResponse
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
    @Operation(summary = "Register User")
    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody command: RegisterUserCommand): ResponseEntity<ApiResponse<UserResponse>> {
        val user = registerUserCommandHandler.handle(command)
        emailVerifcationService.sendVerificationEmail(user)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(success(HttpStatus.CREATED.value(), "회원가입 성공", UserResponse.from(user)))
    }

    @Operation(summary = "email verification")
    @GetMapping("/email-verify")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<String>> {
        emailVerifcationService.verifyToken(token)
        return ResponseEntity.ok(success(200, "이메일 인증 완료"))
    }

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

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<ApiResponse<LoginResponse>> {
        val userId = jwtProvider.getUserId(request.accessToken)
        val email = jwtProvider.getEmail(request.accessToken)
        val (newAccessToken, newRefreshToken) = authQueryService.refresh(userId, email, request.refreshToken)
        val response = LoginResponse(newAccessToken, newRefreshToken)
        return ResponseEntity.ok(success(200, "토큰 재발급 성공", response))
    }

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