package es.unizar.urlshortener.preview

import es.unizar.urlshortener.preview.adapters.fetch.JsoupOpenGraphParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenGraphParserTests {
    private val parser = JsoupOpenGraphParser()

    @Test
    fun `reads open graph title image and url ahead of the document title`() {
        val html =
            """
            <html><head>
              <title>Ignored</title>
              <meta property="og:title" content="Story">
              <meta property="og:image" content="https://cdn.example/story.jpg">
              <meta property="og:url" content="https://example.com/story">
            </head></html>
            """.trimIndent()

        val metadata = parser.parse(html)

        assertEquals("Story", metadata.title)
        assertEquals("https://cdn.example/story.jpg", metadata.image)
        assertEquals("https://example.com/story", metadata.url)
    }

    @Test
    fun `uses the document title when open graph tags are missing`() {
        val metadata = parser.parse("<html><head><title>Plain</title></head></html>")

        assertEquals("Plain", metadata.title)
        assertNull(metadata.image)
        assertNull(metadata.url)
    }

    @Test
    fun `returns empty metadata for a document without a title`() {
        val metadata = parser.parse("<html><head></head></html>")

        assertNull(metadata.title)
        assertNull(metadata.image)
        assertNull(metadata.url)
    }
}
