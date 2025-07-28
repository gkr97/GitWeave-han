package com.example.gitserver.module.user.interfaces.rest


import com.example.gitserver.common.jwt.GitPatAuthenticationFilter
import com.example.gitserver.common.jwt.JwtAuthenticationFilter
import com.example.gitserver.common.jwt.JwtProvider
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.application.command.LoginUserCommand
import com.example.gitserver.module.user.application.command.RegisterUserCommand
import com.example.gitserver.module.user.application.command.handler.LoginUserCommandHandler
import com.example.gitserver.module.user.application.command.handler.RegisterUserCommandHandler
import com.example.gitserver.module.user.application.service.AuthCommandService
import com.example.gitserver.module.user.application.service.AuthQueryService
import com.example.gitserver.module.user.infrastructure.email.EmailVerifcationService
import com.example.gitserver.module.user.interfaces.rest.dto.LoginRequest
import com.example.gitserver.module.user.interfaces.rest.dto.RefreshRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(
    controllers = [UserAuthController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthenticationFilter::class, GitPatAuthenticationFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs
class UserAuthControllerTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) {

    @MockBean private lateinit var registerUserCommandHandler: RegisterUserCommandHandler
    @MockBean private lateinit var loginUserCommandHandler: LoginUserCommandHandler
    @MockBean private lateinit var authCommandService: AuthCommandService
    @MockBean private lateinit var authQueryService: AuthQueryService
    @MockBean private lateinit var emailVerifcationService: EmailVerifcationService
    @MockBean private lateinit var jwtProvider: JwtProvider

    @Test
    fun `회원가입`() {
        val email = "newuser@test.com"
        val password = "password123!"
        val name = "새유저"

        val mockUser = UserFixture.default(id = 2L, email = email, name = name)
        whenever(registerUserCommandHandler.handle(any<RegisterUserCommand>()))
            .thenReturn(mockUser)

        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    RegisterUserCommand(email = email, password = password, name = name)
                ))
        )
            .andExpect(status().isCreated)
            .andDo(
                document(
                    "auth-register",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("email").description("회원가입 이메일"),
                        fieldWithPath("password").description("비밀번호"),
                        fieldWithPath("name").description("유저 이름").optional(),
                        fieldWithPath("providerCode").description("가입 제공자 코드  local, kakao")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("메시지"),
                        fieldWithPath("data.id").description("신규 유저 ID"),
                        fieldWithPath("data.email").description("신규 유저 이메일"),
                        fieldWithPath("data.name").description("유저 이름"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }

    @Test
    fun `로그인`() {
        val email = "user@test.com"
        val password = "password123!"
        val accessToken = "ACCESS_TOKEN"
        val refreshToken = "REFRESH_TOKEN"
        val mockUser = UserFixture.default(id = 1L, email = email, name = "유저", emailVerified = true)

        whenever(
            authCommandService.login(
                any<LoginUserCommand>(),
                any<String>(),
                anyOrNull<String>()
            )
        ).thenReturn(Triple(accessToken, refreshToken, mockUser))

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest(email, password)))
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "auth-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("email").description("로그인 이메일"),
                        fieldWithPath("password").description("비밀번호")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("메시지"),
                        fieldWithPath("data.accessToken").description("JWT AccessToken"),
                        fieldWithPath("data.refreshToken").description("RefreshToken"),
                        fieldWithPath("data.user.id").description("유저 ID"),
                        fieldWithPath("data.user.email").description("유저 이메일"),
                        fieldWithPath("data.user.name").description("유저 이름"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }

    @Test
    fun `리프레시`() {
        val accessToken = "EXPIRED_ACCESS_TOKEN"
        val refreshToken = "OLD_REFRESH_TOKEN"
        val newAccessToken = "NEW_ACCESS_TOKEN"
        val newRefreshToken = "NEW_REFRESH_TOKEN"

        whenever(authQueryService.refresh(any<Long>(), any<String>(), any<String>()))
            .thenReturn(Pair(newAccessToken, newRefreshToken))
        whenever(jwtProvider.getUserId(accessToken)).thenReturn(1L)
        whenever(jwtProvider.getEmail(accessToken)).thenReturn("user@test.com")

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(accessToken, refreshToken)))
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "auth-refresh",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("accessToken").description("만료된 accessToken"),
                        fieldWithPath("refreshToken").description("RefreshToken")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("메시지"),
                        fieldWithPath("data.accessToken").description("새 AccessToken"),
                        fieldWithPath("data.refreshToken").description("새 RefreshToken"),
                        fieldWithPath("data.user").description("유저 정보").optional(),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")

                    )
                )
            )
    }

    @Test
    fun `이메일 인증`() {
        val token = "EMAIL_VERIFICATION_TOKEN"
        mockMvc.perform(
            get("/api/v1/auth/email-verify")
                .param("token", token)
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "auth-email-verify",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    queryParameters(
                        parameterWithName("token").description("이메일 인증 토큰")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("메시지"),
                        fieldWithPath("data").description("결과 문자열(예: '이메일 인증 완료')"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }
}
