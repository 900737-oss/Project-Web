package es.unizar.urlshortener.clicks

import org.springframework.modulith.events.Externalized
import java.time.Instant
import java.util.UUID

/**
 * Entrega al menos una vez: los consumidores deben ignorar un eventId ya procesado.
 * Con el perfil broker, Modulith lo externaliza a un exchange durable de RabbitMQ.
 */
@Externalized(ClickLoggedEvent.TARGET)
data class ClickLoggedEvent(
    val hash: String,
    val occurredAt: Instant = Instant.now(),
    val eventId: UUID = UUID.randomUUID(),
) {
    companion object {
        const val EXCHANGE = "urlshortener.click-logged"
        const val ROUTING_KEY = "clicks.ClickLoggedEvent"
        const val TARGET = "$EXCHANGE::$ROUTING_KEY"
    }
}
