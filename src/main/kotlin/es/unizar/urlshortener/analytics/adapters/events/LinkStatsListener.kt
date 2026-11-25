package es.unizar.urlshortener.analytics.adapters.events

import es.unizar.urlshortener.analytics.application.UpdateLinkStats
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Sigue en proceso: la creación del short URL no pasa por el broker de clicks. */
@Component
class LinkStatsListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    @ApplicationModuleListener
    fun onCreated(event: ShortUrlCreatedEvent) {
        updateLinkStats.onCreated(event)
    }
}

/**
 * Incrementa las stats de [ClickLoggedEvent] en la misma JVM.
 * Solo activo sin perfil broker, para no contar dos veces con el listener de Rabbit
 */
@Component
@Profile("!broker")
class InProcessClickLoggedListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    @ApplicationModuleListener
    fun onClick(event: ClickLoggedEvent) {
        updateLinkStats.onClick(event)
    }
}

@Component
@Profile("!broker")
class InProcessShortUrlUpdatedListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    @ApplicationModuleListener
    fun onUpdated(event: ShortUrlUpdatedEvent) {
        updateLinkStats.onUpdated(event)
    }
}
