package es.unizar.urlshortener.preview.adapters.events

import es.unizar.urlshortener.links.LINK_PREVIEW_REQUESTED_QUEUE
import es.unizar.urlshortener.links.LinkPreviewRequestedEvent
import es.unizar.urlshortener.preview.application.FetchLinkPreview
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("broker")
class LinkPreviewRequestedConsumer(
    private val fetchLinkPreview: FetchLinkPreview,
) {
    @RabbitListener(queues = [LINK_PREVIEW_REQUESTED_QUEUE])
    fun on(event: LinkPreviewRequestedEvent) {
        fetchLinkPreview.fetch(event.hash, event.target)
    }
}
