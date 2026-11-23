package es.unizar.urlshortener.clicks.adapters.events

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.clicks.application.RecordClick
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Guarda el click en proceso.
 * Solo activo sin perfil broker, para no duplicar con el listener de Rabbit.
 */
@Component
@Profile("!broker")
class ClickRecorder(
    private val recordClick: RecordClick,
) {
    @ApplicationModuleListener
    fun on(event: ClickLoggedEvent) {
        recordClick.record(event)
    }
}
