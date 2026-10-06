package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@Profile("docker")
class RedisShortUrlUpdatedListener(
    private val redis: StringRedisTemplate,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onShortUrlUpdated(event: ShortUrlUpdatedEvent) {
        redis.convertAndSend(INVALIDATION_CHANNEL, event.hash)
    }

    companion object {
        const val INVALIDATION_CHANNEL = "urlshortener:redirect:invalidate"
    }
}
