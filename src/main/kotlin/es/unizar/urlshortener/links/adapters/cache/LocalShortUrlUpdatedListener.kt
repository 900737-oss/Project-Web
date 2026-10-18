package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@Profile("!docker")
class LocalShortUrlUpdatedListener(
    private val redirectCache: RedirectCache,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onShortUrlUpdated(event: ShortUrlUpdatedEvent) {
        redirectCache.evict(event.hash)
    }
}
