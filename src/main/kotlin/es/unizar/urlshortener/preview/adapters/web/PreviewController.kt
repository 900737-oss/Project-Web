package es.unizar.urlshortener.preview.adapters.web

import es.unizar.urlshortener.preview.application.GetLinkPreview
import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.PreviewNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.net.URI

data class LinkPreviewResponse(
    val hash: String,
    val status: LinkPreviewStatus,
    val title: String?,
    val image: String?,
    val url: String?,
    val detail: String?,
)

@RestController
class PreviewController(
    private val getLinkPreview: GetLinkPreview,
) {
    @GetMapping("/api/preview/{hash}")
    fun preview(
        @PathVariable hash: String,
    ): LinkPreviewResponse {
        val stored = getLinkPreview.findByHash(hash) ?: throw PreviewNotFoundException(hash)
        return stored.toResponse()
    }

    @ExceptionHandler(PreviewNotFoundException::class)
    fun notFound(ex: PreviewNotFoundException): ResponseEntity<ProblemDetail> {
        val problem =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.message ?: "No preview is stored",
            )
        problem.type = URI.create("https://api.urlshortener.unizar.es/problems/preview-not-found")
        problem.title = "Preview Not Found"
        problem.instance = URI.create("/api/preview/${ex.hash}")
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}

private fun LinkPreview.toResponse() =
    LinkPreviewResponse(
        hash = hash,
        status = status,
        title = title,
        image = image,
        url = url,
        detail = detail,
    )
