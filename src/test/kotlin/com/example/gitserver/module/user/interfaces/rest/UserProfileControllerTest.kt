package com.example.gitserver.module.user.interfaces.rest

import com.example.gitserver.common.jwt.GitPatAuthenticationFilter
import com.example.gitserver.common.jwt.JwtAuthenticationFilter
import com.example.gitserver.fixture.UserFixture
import com.example.gitserver.module.user.application.command.service.UserProfileCommandService
import com.example.gitserver.module.user.domain.CustomUserDetails
import com.example.gitserver.module.user.interfaces.dto.UpdateNameRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*

@WebMvcTest(
    controllers = [UserProfileController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthenticationFilter::class, GitPatAuthenticationFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs
class UserProfileControllerTest @Autowired constructor(
    val mockMvc: MockMvc,
    val objectMapper: ObjectMapper
) {

    @MockBean
    private lateinit var userProfileCommandService: UserProfileCommandService

    private fun setAuthentication(userDetails: CustomUserDetails) {
        val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `내 프로필 조회`() {
        val user = UserFixture.default()
        val userDetails = CustomUserDetails(user)
        setAuthentication(userDetails)

        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk)
            .andDo(
                document(
                    "user-profile-me",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("data.id").description("유저 ID"),
                        fieldWithPath("data.email").description("유저 이메일"),
                        fieldWithPath("data.name").description("유저 이름"),
                        fieldWithPath("data.profileImageUrl").description("프로필 이미지 URL"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }

    @Test
    fun `이름 변경`() {
        val user = UserFixture.default()
        val userDetails = CustomUserDetails(user)
        val request = UpdateNameRequest("새이름")
        setAuthentication(userDetails)

        mockMvc.perform(
            patch("/api/v1/users/me/name")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk)
            .andDo(
                document(
                    "user-profile-update-name",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("name").description("변경할 이름")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("data").description("변경된 이름 값"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }

    @Test
    fun `프로필 이미지 업로드`() {
        val user = UserFixture.default()
        val userDetails = CustomUserDetails(user)
        val file = MockMultipartFile("file", "profile.png", "image/png", byteArrayOf(1, 2, 3))
        val url = "http://localhost:4566/bucket/user-profile-pictures/1/test.png"
        whenever(userProfileCommandService.updateProfileImage(any(), any())).thenReturn(url)

        setAuthentication(userDetails)

        mockMvc.perform(
            multipart("/api/v1/users/me/image")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA)
        ).andExpect(status().isOk)
            .andDo(
                document(
                    "user-profile-upload-image",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestParts(
                        partWithName("file").description("업로드할 프로필 이미지 파일")
                    ),
                    responseFields(
                        fieldWithPath("success").description("요청 성공 여부"),
                        fieldWithPath("code").description("응답 코드"),
                        fieldWithPath("message").description("응답 메시지"),
                        fieldWithPath("data").description("업로드된 이미지 URL"),
                        fieldWithPath("timestamp").description("응답 시간(UTC)")
                    )
                )
            )
    }
}
