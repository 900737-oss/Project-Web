package es.unizar.urlshortener.analytics.adapters.broker

import es.unizar.urlshortener.analytics.application.UpdateLinkStats
import es.unizar.urlshortener.clicks.ClickLoggedEvent
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Cola durable propia de analytics sobre el exchange de [ClickLoggedEvent],
 * aislada del consumidor del log en clicks.
 */
@Configuration
@Profile("broker")
class ClickLoggedBrokerConfiguration {
    @Bean
    fun analyticsClickLoggedQueue(): Queue = Queue(QUEUE, true)

    @Bean
    fun analyticsClickLoggedBinding(analyticsClickLoggedQueue: Queue): Binding =
        BindingBuilder
            .bind(analyticsClickLoggedQueue)
            .to(TopicExchange(ClickLoggedEvent.EXCHANGE))
            .with(ClickLoggedEvent.ROUTING_KEY)

    companion object {
        const val QUEUE = "urlshortener.analytics.click-logged"
    }
}

/** Actualiza las stats cuando [ClickLoggedEvent] llega por RabbitMQ. */
@Component
@Profile("broker")
class ClickLoggedBrokerListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    @RabbitListener(queues = [ClickLoggedBrokerConfiguration.QUEUE])
    fun onClick(event: ClickLoggedEvent) {
        updateLinkStats.onClick(event)
    }
}
