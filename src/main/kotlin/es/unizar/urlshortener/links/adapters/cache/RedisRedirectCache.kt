package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Optional

@Component
@Profile("docker")
class RedisRedirectCache(
    private val redis: StringRedisTemplate,
    @Value("\${urlshortener.links.redirect-cache-ttl:1h}") private val ttl: Duration,
) : RedirectCache {
    override fun findTarget(hash: String): Optional<String> = Optional.ofNullable(redis.opsForValue().get(key(hash)))

    override fun putTarget(
        hash: String,
        target: String,
    ) {
        redis.opsForValue().set(key(hash), target, ttl)
    }

    override fun evict(hash: String) {
        redis.delete(key(hash))
    }

    private fun key(hash: String) = "$KEY_PREFIX$hash"

    private companion object {
        const val KEY_PREFIX = "urlshortener:redirect:"
    }
}
