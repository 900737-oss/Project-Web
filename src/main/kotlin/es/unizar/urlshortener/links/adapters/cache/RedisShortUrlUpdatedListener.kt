package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@Profile("docker")
class RedisShortUrlUpdatedListener(
    private val redis: StringRedisTemplate,
    private val redirectCache: RedirectCache,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onShortUrlUpdated(event: ShortUrlUpdatedEvent) {
        // The key is shared, so this delete is visible to every replica before the response returns.
        // Pub/Sub repeats the eviction in case a replica repopulated the key while the event was in flight.
        redirectCache.evict(event.hash)
        redis.convertAndSend(INVALIDATION_CHANNEL, event.hash)
    }

    companion object {
        const val INVALIDATION_CHANNEL = "urlshortener:redirect:invalidate"
    }
}
