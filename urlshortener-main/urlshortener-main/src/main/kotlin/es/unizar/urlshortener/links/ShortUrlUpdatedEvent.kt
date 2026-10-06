package es.unizar.urlshortener.links

import java.time.Instant

/**
 * Published after a short URL's active state changes.
 */
data class ShortUrlUpdatedEvent(
    val hash: String,
    val active: Boolean,
    val updatedAt: Instant = Instant.now(),
)
