import com.example.gitserver.module.user.application.query.JwtQueryService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import javax.crypto.SecretKey

class JwtQueryServiceTest {

    private lateinit var jwtQueryService: JwtQueryService
    private lateinit var secretKey: SecretKey

    @BeforeEach
    fun setup() {
        secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
        jwtQueryService = JwtQueryService(Base64.getEncoder().encodeToString(secretKey.encoded))
    }

    private fun generateToken(userId: Long): String {
        val now = Date()
        val exp = Date(now.time + 60 * 60 * 1000)
        return Jwts.builder()
            .claim("userId", userId)
            .setIssuedAt(now)
            .setExpiration(exp)
            .signWith(secretKey)
            .compact()
    }

    @Test
    fun `resolveUserIdByBearer - 정상 Bearer 토큰에서 userId 추출`() {
        val token = generateToken(123L)
        val bearer = "Bearer $token"

        val userId = jwtQueryService.resolveUserIdByBearer(bearer)

        userId shouldBe 123L
    }

    @Test
    fun `resolveUserIdByBearer - Bearer 없으면 null`() {
        jwtQueryService.resolveUserIdByBearer(null) shouldBe null
        jwtQueryService.resolveUserIdByBearer("InvalidToken") shouldBe null
    }

    @Test
    fun `resolveUserIdByBearer - 잘못된 토큰이면 null`() {
        val invalidBearer = "Bearer not-a-valid-token"
        jwtQueryService.resolveUserIdByBearer(invalidBearer) shouldBe null
    }
}
