package es.unizar.urlshortener.clicks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.modulith.events.Externalized

class ClickLoggedEventTests {
    @Test
    fun `the click event is externalized to the click-logged exchange`() {
        val externalized = ClickLoggedEvent::class.java.getAnnotation(Externalized::class.java)

        assertEquals(ClickLoggedEvent.TARGET, externalized?.value)
        assertEquals("urlshortener.click-logged::clicks.ClickLoggedEvent", externalized?.value)
    }
}
