package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
open class LinkPreviewStoreConcurrencyTests {
    @Autowired
    lateinit var store: LinkPreviewStore

    @Test
    fun `concurrent pending inserts for one hash create one row without errors`() {
        val hash = "preview-race-${UUID.randomUUID()}"
        val pool = Executors.newFixedThreadPool(CONCURRENT_INSERTS)
        val start = CountDownLatch(1)
        val futures =
            (0 until CONCURRENT_INSERTS).map {
                pool.submit<Boolean> {
                    start.await()
                    store.insertPending(hash, "https://example.com/$hash")
                }
            }

        start.countDown()
        val inserted = futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, inserted.count { it })
        assertEquals(CONCURRENT_INSERTS - 1, inserted.count { !it })
        val stored = store.findByHash(hash)
        assertNotNull(stored)
        assertEquals(LinkPreviewStatus.PENDING, stored?.status)
    }

    private companion object {
        const val CONCURRENT_INSERTS = 16
        const val TIMEOUT_SECONDS = 20L
    }
}
