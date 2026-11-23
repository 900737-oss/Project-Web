package es.unizar.urlshortener.links

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.links.adapters.cache.InMemoryRedirectCache
import es.unizar.urlshortener.links.adapters.web.LinkController
import es.unizar.urlshortener.links.application.LinkService
import es.unizar.urlshortener.links.application.ShortCodeSource
import es.unizar.urlshortener.links.application.ShortUrlStore
import es.unizar.urlshortener.links.application.UrlValidator
import es.unizar.urlshortener.links.domain.ShortUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class RedirectCacheHitTests {
    @Test
    fun `a later HTTP redirect uses the cached Location without another database lookup`() {
        val hash = "cached-link"
        val target = "https://example.com/cached-target"
        val store = mock(ShortUrlStore::class.java)
        val cache = InMemoryRedirectCache()
        val published = mutableListOf<Any>()
        val events = ApplicationEventPublisher { published.add(it) }
        val service = LinkService(store, cache, UrlValidator { true }, ShortCodeSource { hash }, events)
        val http = MockMvcBuilders.standaloneSetup(LinkController(service, service, service, "test-admin-token")).build()
        `when`(store.findByHash(hash)).thenReturn(Optional.of(ShortUrl(hash = hash, target = target)))

        repeat(2) {
            http
                .perform(get("/$hash"))
                .andExpect(status().isTemporaryRedirect)
                .andExpect(header().string("Location", target))
        }

        verify(store, times(1)).findByHash(hash)
        assertEquals(target, cache.findTarget(hash).orElseThrow())
        assertEquals(listOf(hash, hash), published.filterIsInstance<ClickLoggedEvent>().map { it.hash })
    }
}
