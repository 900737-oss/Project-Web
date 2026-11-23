package es.unizar.urlshortener.clicks

import es.unizar.urlshortener.clicks.adapters.persistence.ClickEntity
import es.unizar.urlshortener.clicks.adapters.persistence.JpaClickRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@Transactional
class ClickPersistenceTests {
    @Autowired
    lateinit var repository: JpaClickRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `legacy clicks without event ids remain distinct and receive timestamps`() {
        val hash = "legacy-${UUID.randomUUID()}"
        val earliest = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val first = repository.saveAndFlush(ClickEntity(hash = hash))
        val second = repository.saveAndFlush(ClickEntity(hash = hash))
        val latest = Instant.now().plusSeconds(1)
        val firstId = requireNotNull(first.id)
        val secondId = requireNotNull(second.id)
        entityManager.clear()

        assertNotEquals(firstId, secondId)
        listOf(firstId, secondId).forEach { id ->
            val stored = repository.findById(id).orElseThrow()
            assertEquals(hash, stored.hash)
            assertNull(stored.eventId)
            assertFalse(stored.occurredAt.isBefore(earliest))
            assertFalse(stored.occurredAt.isAfter(latest))
        }
    }
}
