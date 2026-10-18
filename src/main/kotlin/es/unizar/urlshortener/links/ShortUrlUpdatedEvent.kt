package es.unizar.urlshortener.links

import org.springframework.modulith.events.Externalized
import java.time.Instant

/**
 * Published after a short URL's active state changes.
 * With the broker profile, Modulith externalizes it to a durable RabbitMQ exchange.
 */
@Externalized(ShortUrlUpdatedEvent.TARGET)
data class ShortUrlUpdatedEvent(
    val hash: String,
    val active: Boolean,
    val updatedAt: Instant = Instant.now(),
) {
    companion object {
        const val EXCHANGE = "urlshortener.short-url-updated"
        const val ROUTING_KEY = "links.ShortUrlUpdatedEvent"
        const val TARGET = "$EXCHANGE::$ROUTING_KEY"
    }
}
