import com.example.gitserver.module.user.PersonalAccessToken
import com.example.gitserver.module.user.application.service.PatService
import com.example.gitserver.module.user.domain.User
import com.example.gitserver.module.user.exception.UserNotFoundException
import com.example.gitserver.module.user.infrastructure.persistence.PersonalAccessTokenRepository
import com.example.gitserver.module.user.infrastructure.persistence.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.*

class PatServiceTest {

    private lateinit var personalAccessTokenRepository: PersonalAccessTokenRepository
    private lateinit var userRepository: UserRepository
    private lateinit var patService: PatService

    private val user = User(
        id = 1L,
        email = "test@example.com",
        passwordHash = "pw",
        name = "name",
        profileImageUrl = null,
        bio = null,
        websiteUrl = null,
        timezone = null,
        emailVerified = true,
        isActive = true,
        isDeleted = false,
        createdAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        personalAccessTokenRepository = mock()
        userRepository = mock()
        patService = PatService(personalAccessTokenRepository, userRepository)
    }

    @Test
    fun `issuePat 정상 생성`() {
        // given
        val rawToken = "plaintext"
        val tokenHash = patServiceTestHelper_hash(rawToken)
        val pat = PersonalAccessToken(
            userId = user.id,
            tokenHash = tokenHash,
            description = "desc"
        )
        whenever(personalAccessTokenRepository.save(any<PersonalAccessToken>())).thenReturn(pat)

        // when
        val result = patService.issuePat(user.id, rawToken, description = "desc")

        // then
        assert(result.userId == user.id)
        assert(result.tokenHash == tokenHash)
        assert(result.description == "desc")
        verify(personalAccessTokenRepository).save(any())
    }

    @Test
    fun `validatePat 만료되지 않은 토큰이면 true`() {
        // given
        val rawToken = "pat123"
        val tokenHash = patServiceTestHelper_hash(rawToken)
        val expiresAt = LocalDateTime.now().plusDays(1)
        val pat = PersonalAccessToken(
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = expiresAt
        )
        whenever(personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(user.id, tokenHash)).thenReturn(pat)

        // when
        val result = patService.validatePat(user.id, rawToken)

        // then
        assert(result)
    }

    @Test
    fun `validatePat 만료된 토큰이면 false`() {
        // given
        val rawToken = "patExpired"
        val tokenHash = patServiceTestHelper_hash(rawToken)
        val expiresAt = LocalDateTime.now().minusDays(1)
        val pat = PersonalAccessToken(
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = expiresAt
        )
        whenever(personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(user.id, tokenHash)).thenReturn(pat)

        // when
        val result = patService.validatePat(user.id, rawToken)

        // then
        assert(!result)
    }

    @Test
    fun `validatePat 널 리턴시 false`() {
        // given
        val rawToken = "notfound"
        val tokenHash = patServiceTestHelper_hash(rawToken)
        whenever(personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(user.id, tokenHash)).thenReturn(null)

        // when
        val result = patService.validatePat(user.id, rawToken)

        // then
        assert(!result)
    }

    @Test
    fun `deactivatePat 정상작동`() {
        // given
        val patId = 100L
        val pat = PersonalAccessToken(
            id = patId,
            userId = user.id,
            tokenHash = "hash",
            description = "desc",
            isActive = true
        )
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))
        whenever(personalAccessTokenRepository.findById(patId)).thenReturn(Optional.of(pat))
        whenever(personalAccessTokenRepository.save(pat)).thenReturn(pat)

        // when
        patService.deactivatePat(patId, user.id)

        // then
        assert(!pat.isActive)
        verify(personalAccessTokenRepository).save(pat)
    }

    @Test
    fun `deactivatePat 유저없으면 예외`() {
        val patId = 100L
        whenever(userRepository.findById(user.id)).thenReturn(Optional.empty())

        assertThrows<UserNotFoundException> {
            patService.deactivatePat(patId, user.id)
        }
    }

    @Test
    fun `resolveUserIdByAuthHeader 정상 파싱 및 검증`() {
        // given
        val rawToken = "pat-resolve"
        val tokenHash = patServiceTestHelper_hash(rawToken)
        val pat = PersonalAccessToken(
            userId = user.id,
            tokenHash = tokenHash
        )
        val credentials = "${user.email}:$rawToken"
        val basicHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())

        whenever(userRepository.findByEmail(user.email)).thenReturn(user)
        whenever(personalAccessTokenRepository.findByUserIdAndTokenHashAndIsActiveTrue(user.id, tokenHash)).thenReturn(pat)

        // when
        val userId = patService.resolveUserIdByAuthHeader(basicHeader)

        // then
        assert(userId == user.id)
    }

    @Test
    fun `resolveUserIdByAuthHeader 파싱 실패 또는 잘못된 토큰은 널을 주입`() {
        whenever(userRepository.findByEmail("noone@email.com")).thenReturn(null)

        val badHeader = "Basic " + Base64.getEncoder().encodeToString("noone@email.com:abc".toByteArray())
        val userId = patService.resolveUserIdByAuthHeader(badHeader)
        assert(userId == null)
    }

    private fun patServiceTestHelper_hash(rawToken: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
