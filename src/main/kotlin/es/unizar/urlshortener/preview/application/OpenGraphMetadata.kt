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

@SecondaryPort
fun interface OpenGraphClient {
    /** Downloads [target] and parses its Open Graph metadata. */
    fun fetch(target: String): OpenGraphMetadata
}

class OpenGraphFetchException(
    target: String,
    cause: Throwable? = null,
) : RuntimeException("Could not fetch $target", cause)
