package es.unizar.urlshortener.clicks.adapters.scheduling

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Reenvía ClickLoggedEvent incompletos del registro Modulith cuando el broker vuelve.
 * Sin esto, Compose deja el click pendiente hasta un resubmit manual o un reinicio
 */
@Component
@Profile("broker")
class IncompleteClickLoggedRepublisher(
    private val incompletePublications: IncompleteEventPublications,
) {
    @Scheduled(fixedDelayString = "\${urlshortener.clicks.incomplete-publication-resubmit-delay-ms:5000}")
    fun resubmitIncompleteClicks() {
        incompletePublications.resubmitIncompletePublications { publication ->
            publication.event is ClickLoggedEvent
        }
    }
}
