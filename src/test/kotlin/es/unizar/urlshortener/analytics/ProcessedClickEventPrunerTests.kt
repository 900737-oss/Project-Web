package es.unizar.urlshortener.analytics

import es.unizar.urlshortener.analytics.adapters.scheduling.ProcessedClickEventPruner
import es.unizar.urlshortener.analytics.application.PruneProcessedClickEvents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class ProcessedClickEventPrunerTests {
    @Test
    fun `scheduled callback applies the configured retention on each invocation`() {
        val retention = Duration.ofHours(37)
        val requestedRetentions = mutableListOf<Duration>()
        val prune =
            PruneProcessedClickEvents {
                requestedRetentions += it
                1
            }
        val scheduled = ProcessedClickEventPruner(prune, retention)

        repeat(2) { scheduled.prune() }

        assertEquals(listOf(retention, retention), requestedRetentions)
    }
}
