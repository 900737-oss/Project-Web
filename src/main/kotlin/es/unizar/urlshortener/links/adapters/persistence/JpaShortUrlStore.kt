package es.unizar.urlshortener.links.adapters.persistence

import es.unizar.urlshortener.links.application.DeactivationOutcome
import es.unizar.urlshortener.links.application.ShortUrlStore
import es.unizar.urlshortener.links.domain.ShortUrl
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class JpaShortUrlStore(
    private val repository: JpaShortUrlRepository,
) : ShortUrlStore {
    override fun insert(shortUrl: ShortUrl): ShortUrl = repository.save(shortUrl.toEntity()).toDomain()

    override fun findByHash(hash: String): Optional<ShortUrl> = repository.findById(hash).filter { it.active }.map { it.toDomain() }

    override fun deactivate(hash: String): DeactivationOutcome {
        val entity = repository.findById(hash).orElse(null)
        return when {
            entity == null -> DeactivationOutcome.NOT_FOUND
            !entity.active -> DeactivationOutcome.ALREADY_DEACTIVATED
            else -> {
                entity.active = false
                repository.save(entity)
                DeactivationOutcome.DEACTIVATED
            }
        }
    }
}
