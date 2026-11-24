package es.unizar.urlshortener.analytics

import es.unizar.urlshortener.analytics.adapters.persistence.JpaLinkStatsRepository
import es.unizar.urlshortener.analytics.adapters.persistence.JpaProcessedClickEventRepository
import es.unizar.urlshortener.analytics.adapters.persistence.LinkStatsEntity
import es.unizar.urlshortener.analytics.adapters.persistence.ProcessedClickEventEntity
import es.unizar.urlshortener.analytics.application.LinkStats
import es.unizar.urlshortener.analytics.application.LinkStatsStore
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@Transactional
class AnalyticsPersistenceTests {
    @Autowired
    lateinit var stats: JpaLinkStatsRepository

    @Autowired
    lateinit var processed: JpaProcessedClickEventRepository

    @Autowired
    lateinit var store: LinkStatsStore

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `stats persistence preserves initial defaults and inactive click totals`() {
        val initialHash = "initial-${UUID.randomUUID()}"
        val inactiveHash = "inactive-${UUID.randomUUID()}"
        stats.saveAllAndFlush(
            listOf(
                LinkStatsEntity(hash = initialHash),
                LinkStatsEntity(hash = inactiveHash, totalClicks = 17, active = false),
            ),
        )
        entityManager.clear()

        assertEquals(LinkStats(hash = initialHash, totalClicks = 0), store.findByHash(initialHash))
        assertEquals(LinkStats(hash = inactiveHash, totalClicks = 17, active = false), store.findByHash(inactiveHash))
    }

    @Test
    fun `processed id retention deletes strictly older timestamps and preserves the cutoff`() {
        val clock = Clock.fixed(Instant.parse("2024-01-08T00:00:00Z"), ZoneOffset.UTC)
        val cutoff = clock.instant().minus(Duration.ofDays(7))
        val expired = ProcessedClickEventEntity(UUID.randomUUID(), cutoff.minusSeconds(1))
        val boundary = ProcessedClickEventEntity(UUID.randomUUID(), cutoff)
        val recent = ProcessedClickEventEntity(UUID.randomUUID())
        processed.saveAllAndFlush(listOf(expired, boundary, recent))

        store.deleteProcessedClicksBefore(cutoff)
        entityManager.clear()

        assertFalse(processed.existsById(expired.eventId))
        assertEquals(cutoff, processed.findById(boundary.eventId).orElseThrow().processedAt)
        val retained = processed.findById(recent.eventId).orElseThrow()
        assertEquals(recent.eventId, retained.eventId)
        assertTrue(Duration.between(recent.processedAt, retained.processedAt).abs() < Duration.ofMillis(1))
        assertTrue(retained.processedAt.isAfter(cutoff))
    }
}
