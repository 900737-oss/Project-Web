package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.application.LinkService
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.ShortCodeSource
import es.unizar.urlshortener.links.application.ShortUrlStore
import es.unizar.urlshortener.links.application.UrlValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher

class LinkServiceValidationTests {
    @Test
    fun `blank input is rejected before allocating a code or writing data`() {
        val store = mock(ShortUrlStore::class.java)
        val cache = mock(RedirectCache::class.java)
        val validator = mock(UrlValidator::class.java)
        val codes = mock(ShortCodeSource::class.java)
        val events = mock(ApplicationEventPublisher::class.java)
        val service = LinkService(store, cache, validator, codes, events)

        val failure = assertThrows(IllegalArgumentException::class.java) { service.create(" \t\n ") }

        assertEquals("url must not be blank", failure.message)
        verifyNoInteractions(store, cache, validator, codes, events)
    }
}
