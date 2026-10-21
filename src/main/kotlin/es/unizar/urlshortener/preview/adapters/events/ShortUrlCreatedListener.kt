package es.unizar.urlshortener.preview.adapters.events

import es.unizar.urlshortener.links.ShortUrlCreatedEvent
import es.unizar.urlshortener.preview.application.RecordPendingLinkPreview
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class ShortUrlCreatedListener(
    private val recordPendingLinkPreview: RecordPendingLinkPreview,
) {
    @ApplicationModuleListener
    fun onCreated(event: ShortUrlCreatedEvent) {
        recordPendingLinkPreview.onShortUrlCreated(event.hash, event.target)
    }
}
