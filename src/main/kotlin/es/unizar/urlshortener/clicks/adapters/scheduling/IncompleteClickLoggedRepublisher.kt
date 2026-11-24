package es.unizar.urlshortener.clicks.adapters.scheduling

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${urlshortener.clicks.incomplete-publication-resubmit-delay-ms:5000}")
    fun resubmitIncompleteClicks() {
        try {
            incompletePublications.resubmitIncompletePublications { publication ->
                publication.event is ClickLoggedEvent
            }
        } catch (ex: Exception) {
            log.debug("Incomplete ClickLoggedEvent resubmit skipped: {}", ex.message)
        }
    }
}
