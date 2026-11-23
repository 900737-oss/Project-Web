package es.unizar.urlshortener.links.adapters.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface JpaShortUrlRepository : JpaRepository<ShortUrlEntity, String> {
    // Keep a concurrent deactivation from committing before this transaction fills the cache.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select link from ShortUrlEntity link where link.hash = :hash")
    fun findForRedirect(hash: String): Optional<ShortUrlEntity>
}
