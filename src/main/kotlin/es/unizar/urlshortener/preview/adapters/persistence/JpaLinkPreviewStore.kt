package es.unizar.urlshortener.preview.adapters.persistence

import es.unizar.urlshortener.preview.application.LinkPreview
import es.unizar.urlshortener.preview.application.LinkPreviewStatus
import es.unizar.urlshortener.preview.application.LinkPreviewStore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException

@Component
class JpaLinkPreviewStore(
    private val repository: JpaLinkPreviewRepository,
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : LinkPreviewStore {
    private val insertTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    override fun findByHash(hash: String): LinkPreview? = repository.findById(hash).map { it.toDomain() }.orElse(null)

    override fun insertPending(
        hash: String,
        target: String,
    ): Boolean {
        try {
            return insertTransaction.execute {
                jdbc.update(
                    "insert into link_preview (hash, target, status) values (?, ?, ?)",
                    hash,
                    target,
                    LinkPreviewStatus.PENDING.name,
                )
                true
            }
        } catch (ex: DataIntegrityViolationException) {
            if (ex.isUniqueConstraintViolation()) return false
            throw ex
        }
    }

    override fun save(preview: LinkPreview) {
        repository.save(preview.toEntity())
    }
}

private fun DataIntegrityViolationException.isUniqueConstraintViolation(): Boolean {
    var cause: Throwable? = this
    while (cause != null) {
        if (cause is SQLException && cause.sqlState == UNIQUE_VIOLATION_SQL_STATE) return true
        cause = cause.cause
    }
    return false
}

private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
