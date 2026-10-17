package es.unizar.urlshortener.preview.application

import org.jmolecules.architecture.hexagonal.SecondaryPort

/** Title, image, and canonical URL read from one HTML document. */
data class OpenGraphMetadata(
    val title: String?,
    val image: String?,
    val url: String?,
)

@SecondaryPort
fun interface OpenGraphParser {
    fun parse(html: String): OpenGraphMetadata
}
