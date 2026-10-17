package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class LinkPreviewStoreTests {
    @Autowired
    lateinit var store: LinkPreviewStore

    @Test
    fun `inserts one pending row and leaves it unchanged on a second insert`() {
        assertNull(store.findByHash("abc"))

        assertTrue(store.insertPending("abc", "https://example.com/story"))

        val stored = store.findByHash("abc")
        assertEquals("abc", stored?.hash)
        assertEquals("https://example.com/story", stored?.target)
        assertEquals(LinkPreviewStatus.PENDING, stored?.status)
        assertNull(stored?.title)
        assertNull(stored?.image)
        assertNull(stored?.url)
        assertNull(stored?.detail)

        assertFalse(store.insertPending("abc", "https://example.com/other"))
        assertEquals("https://example.com/story", store.findByHash("abc")?.target)
        assertEquals(LinkPreviewStatus.PENDING, store.findByHash("abc")?.status)
    }
}
