package es.unizar.urlshortener.preview.adapters.fetch

import es.unizar.urlshortener.preview.application.OpenGraphClient
import es.unizar.urlshortener.preview.application.OpenGraphFetchException
import es.unizar.urlshortener.preview.application.OpenGraphMetadata
import es.unizar.urlshortener.preview.application.OpenGraphParser
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class HttpOpenGraphClient(
    private val parser: OpenGraphParser,
    private val circuitBreaker: CircuitBreaker,
) : OpenGraphClient {
    private val http =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()

    override fun fetch(target: String): OpenGraphMetadata =
        try {
            circuitBreaker.executeCallable { download(target) }
        } catch (ex: CallNotPermittedException) {
            throw OpenGraphFetchException(target, ex)
        }

    private fun download(target: String): OpenGraphMetadata {
        try {
            val request =
                HttpRequest
                    .newBuilder(URI.create(target))
                    .timeout(READ_TIMEOUT)
                    .GET()
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() >= 400) {
                response.body().close()
                throw OpenGraphFetchException(target)
            }
            val html = response.body().use { readLimited(target, it) }
            return parser.parse(html)
        } catch (ex: OpenGraphFetchException) {
            throw ex
        } catch (ex: Exception) {
            throw OpenGraphFetchException(target, ex)
        }
    }

    private fun readLimited(
        target: String,
        body: InputStream,
    ): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        var total = 0
        while (true) {
            val read = body.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BODY_BYTES) throw OpenGraphFetchException(target)
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8)
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(2)
        const val MAX_BODY_BYTES: Int = 1_000_000
        const val CHUNK_BYTES: Int = 8_192
    }
}
