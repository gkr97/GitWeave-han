package com.example.gitserver.module.repository.infrastructure.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RedisCountService(
    private val redis: StringRedisTemplate
) {
    private fun repoStarsKey(repoId: Long) = "repo:$repoId:stars"

    fun incrementRepoStars(repoId: Long, expire: Duration? = null): Int {
        val key = repoStarsKey(repoId)
        val existed = redis.hasKey(key)
        val newVal = redis.opsForValue().increment(key) ?: 0L
        if (!existed && expire != null) {
            redis.expire(key, expire)
        }
        return if (newVal > Int.MAX_VALUE) Int.MAX_VALUE else newVal.toInt()
    }

    fun incrementRepoStars(repoId: Int, expire: Duration? = null): Int =
        incrementRepoStars(repoId.toLong(), expire)

    fun decrementRepoStars(repoId: Long): Int {
        val key = repoStarsKey(repoId)
        val value = redis.opsForValue().decrement(key) ?: 0L
        return if (value < 0L) {
            redis.opsForValue().set(key, "0")
            0
        } else {
            if (value > Int.MAX_VALUE) Int.MAX_VALUE else value.toInt()
        }
    }

    fun decrementRepoStars(repoId: Int): Int = decrementRepoStars(repoId.toLong())

    fun getRepoStars(repoId: Long): Int {
        val v = redis.opsForValue().get(repoStarsKey(repoId)) ?: return -1
        return try {
            val l = v.toLong()
            if (l < 0L) -1 else if (l > Int.MAX_VALUE) Int.MAX_VALUE else l.toInt()
        } catch (e: NumberFormatException) {
            -1
        }
    }

    fun getRepoStars(repoId: Int): Int = getRepoStars(repoId.toLong())

    fun setRepoStars(repoId: Long, count: Int, expire: Duration? = null) {
        val key = repoStarsKey(repoId)
        if (expire != null) {
            redis.opsForValue().set(key, count.toString(), expire)
        } else {
            redis.opsForValue().set(key, count.toString())
        }
    }

    fun setRepoStars(repoId: Int, count: Int, expire: Duration? = null) =
        setRepoStars(repoId.toLong(), count, expire)

    fun deleteRepoStarsKey(repoId: Long) {
        redis.delete(repoStarsKey(repoId))
    }

    fun deleteRepoStarsKey(repoId: Int) = deleteRepoStarsKey(repoId.toLong())
}
