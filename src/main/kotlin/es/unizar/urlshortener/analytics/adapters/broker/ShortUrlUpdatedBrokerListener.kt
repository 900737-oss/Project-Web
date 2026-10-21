package es.unizar.urlshortener.analytics.adapters.broker

import es.unizar.urlshortener.analytics.application.UpdateLinkStats
import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Configuration
@Profile("broker")
class ShortUrlUpdatedBrokerConfiguration {
    @Bean
    fun analyticsShortUrlUpdatedQueue(): Queue = Queue(QUEUE, true)

    @Bean
    fun analyticsShortUrlUpdatedBinding(analyticsShortUrlUpdatedQueue: Queue): Binding =
        BindingBuilder
            .bind(analyticsShortUrlUpdatedQueue)
            .to(TopicExchange(ShortUrlUpdatedEvent.EXCHANGE))
            .with(ShortUrlUpdatedEvent.ROUTING_KEY)

    companion object {
        const val QUEUE = "urlshortener.analytics.short-url-updated"
    }
}

@Component
@Profile("broker")
class ShortUrlUpdatedBrokerListener(
    private val updateLinkStats: UpdateLinkStats,
) {
    @RabbitListener(queues = [ShortUrlUpdatedBrokerConfiguration.QUEUE])
    fun onUpdated(event: ShortUrlUpdatedEvent) {
        updateLinkStats.onUpdated(event)
    }
}
