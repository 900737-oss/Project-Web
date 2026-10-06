package es.unizar.urlshortener.links.application

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.domain.ShortUrl
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class LinkService(
    private val store: ShortUrlStore,
    private val redirectCache: RedirectCache,
    private val validator: UrlValidator,
    private val codes: ShortCodeSource,
    private val events: ApplicationEventPublisher,
) : CreateShortUrl,
    RedirectShortUrl,
    DeactivateShortUrl {
    @Transactional
    override fun create(url: String): CreatedShortUrl {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "url must not be blank" }
        if (!validator.isValid(trimmed)) {
            throw InvalidUrlException(trimmed)
        }
        val stored = store.insert(ShortUrl(hash = codes.next(), target = trimmed))
        events.publishEvent(ShortUrlCreatedEvent(hash = stored.hash, target = stored.target))
        return CreatedShortUrl(hash = stored.hash, target = stored.target)
    }

    @Transactional
    override fun redirect(hash: String): Redirection {
        val target =
            redirectCache.findTarget(hash).orElseGet {
                val shortUrl = store.findByHash(hash).orElseThrow { LinkNotFoundException(hash) }
                redirectCache.putTarget(hash, shortUrl.target)
                shortUrl.target
            }
        events.publishEvent(ClickLoggedEvent(hash = hash, occurredAt = Instant.now()))
        return Redirection(target = target)
    }

    @Transactional
    override fun deactivate(hash: String) {
        when (store.deactivate(hash)) {
            DeactivationOutcome.DEACTIVATED ->
                events.publishEvent(ShortUrlUpdatedEvent(hash = hash, active = false))
            DeactivationOutcome.ALREADY_DEACTIVATED -> Unit
            DeactivationOutcome.NOT_FOUND -> throw LinkNotFoundException(hash)
        }
    }
}
