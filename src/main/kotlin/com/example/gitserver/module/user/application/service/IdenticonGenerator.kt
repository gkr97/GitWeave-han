package com.example.gitserver.module.user.application.service

import com.example.gitserver.module.user.infrastructure.s3.S3Uploader
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

@Component
class IdenticonGenerator(
) {

    companion object {
        private const val GRID = 5
    }


    fun generate(seed: String, size: Int = 256): ByteArray {
        val digest = sha256(seed)
        val cell = size / GRID
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0xF3, 0xF4, 0xF6)
        g.fillRect(0, 0, size, size)

        val r = (digest[0].toUByte().toInt() + 64).coerceIn(64, 192)
        val gC = (digest[1].toUByte().toInt() + 64).coerceIn(64, 192)
        val b = (digest[2].toUByte().toInt() + 64).coerceIn(64, 192)
        val fg = Color(r, gC, b)

        var bitIdx = 0
        for (y in 0 until GRID) {
            for (x in 0 until (GRID + 1) / 2) {
                val fill = ((digest[3 + bitIdx / 8].toInt() ushr (bitIdx % 8)) and 1) == 1
                bitIdx++
                if (fill) {
                    g.color = fg
                    val px = x * cell
                    val py = y * cell
                    g.fillRect(px, py, cell, cell)
                    val mx = (GRID - 1 - x) * cell
                    if (mx != px) g.fillRect(mx, py, cell, cell)
                }
            }
        }
        g.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)


        return baos.toByteArray()
    }


    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
}
