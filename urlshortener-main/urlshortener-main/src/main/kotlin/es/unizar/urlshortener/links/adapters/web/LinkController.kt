package es.unizar.urlshortener.links.adapters.web

import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.DeactivateShortUrl
import es.unizar.urlshortener.links.application.InvalidUrlException
import es.unizar.urlshortener.links.application.LinkNotFoundException
import es.unizar.urlshortener.links.application.RedirectShortUrl
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

data class ShortUrlResponse(
    val url: String,
    val hash: String,
)

@RestController
class LinkController(
    private val createShortUrl: CreateShortUrl,
    private val redirectShortUrl: RedirectShortUrl,
    private val deactivateShortUrl: DeactivateShortUrl,
    @Value("\${urlshortener.admin.token:}") private val adminToken: String,
) {
    @PostMapping(
        "/api/link",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun create(
        @RequestParam url: String,
    ): ResponseEntity<ShortUrlResponse> {
        val created = createShortUrl.create(url)
        val location =
            ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/{hash}")
                .buildAndExpand(created.hash)
                .toUri()
        return ResponseEntity.created(location).body(
            ShortUrlResponse(url = location.toString(), hash = created.hash),
        )
    }

    @GetMapping("/{hash}")
    fun redirect(
        @PathVariable hash: String,
    ): ResponseEntity<Void> {
        val redirection = redirectShortUrl.redirect(hash)
        val headers = HttpHeaders()
        headers.location = URI.create(redirection.target)
        return ResponseEntity(headers, HttpStatus.TEMPORARY_REDIRECT)
    }

    @PostMapping("/api/link/{hash}/deactivate")
    fun deactivate(
        @PathVariable hash: String,
        @RequestHeader(name = "X-Admin-Token", required = false) providedToken: String?,
    ): ResponseEntity<Map<String, String>> {
        if (!isAuthorized(providedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "unauthorized"))
        }
        deactivateShortUrl.deactivate(hash)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(InvalidUrlException::class)
    fun invalidUrl(ex: InvalidUrlException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "invalid url")))

    @ExceptionHandler(LinkNotFoundException::class)
    fun notFound(ex: LinkNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "not found")))

    private fun isAuthorized(providedToken: String?): Boolean {
        if (adminToken.isBlank() || providedToken.isNullOrBlank()) return false
        return MessageDigest.isEqual(adminToken.toByteArray(UTF_8), providedToken.toByteArray(UTF_8))
    }
}
