package es.unizar.urlshortener.preview.adapters.fetch

import es.unizar.urlshortener.preview.application.OpenGraphMetadata
import es.unizar.urlshortener.preview.application.OpenGraphParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component

@Component
class JsoupOpenGraphParser : OpenGraphParser {
    override fun parse(html: String): OpenGraphMetadata {
        val document = Jsoup.parse(html)
        return OpenGraphMetadata(
            title = meta(document, "og:title") ?: document.title().ifBlank { null },
            image = meta(document, "og:image"),
            url = meta(document, "og:url"),
        )
    }

    private fun meta(
        document: Document,
        property: String,
    ): String? = document.selectFirst("meta[property=$property]")?.attr("content")?.ifBlank { null }
}
