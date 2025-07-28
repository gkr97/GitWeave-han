import com.example.gitserver.module.user.application.service.JwtService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

class JwtServiceTest {

    private val secretKey = "test_secret_very_secret_key_1234654253123123"
    private val jwtService = JwtService(secretKey)

    private fun createToken(userId: Long): String {
        return Jwts.builder()
            .setClaims(mapOf("userId" to userId))
            .setSubject("user")
            .setIssuedAt(Date())
            .signWith(SignatureAlgorithm.HS256, secretKey.toByteArray())
            .compact()
    }

    @Test
    fun `Bearer 토큰이 유효할 때 userId 리턴`() {
        // given
        val userId = 42L
        val token = createToken(userId)
        val bearer = "Bearer $token"

        // when
        val result = jwtService.resolveUserIdByBearer(bearer)

        // then
        assertEquals(userId, result)
    }

    @Test
    fun `토큰 값이 없으면 null 리턴`() {
        val result = jwtService.resolveUserIdByBearer(null)
        assertEquals(null, result)
    }

    @Test
    fun `Bearer prefix가 아니면 null 리턴`() {
        val result = jwtService.resolveUserIdByBearer("Basic abcd")
        assertEquals(null, result)
    }

    @Test
    fun `잘못된 JWT 토큰이면 null 리턴`() {
        val result = jwtService.resolveUserIdByBearer("Bearer invalid.token.value")
        assertEquals(null, result)
    }
}
