package es.unizar.urlshortener.clicks.adapters.broker

import es.unizar.urlshortener.clicks.ClickLoggedEvent
import es.unizar.urlshortener.clicks.application.RecordClick
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
class ClickLoggedBrokerConfiguration {
    @Bean
    fun clickLoggedExchange(): TopicExchange = TopicExchange(ClickLoggedEvent.EXCHANGE, true, false)

    @Bean
    fun clicksClickLoggedQueue(): Queue = Queue(QUEUE, true)

    @Bean
    fun clicksClickLoggedBinding(
        clicksClickLoggedQueue: Queue,
        clickLoggedExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(clicksClickLoggedQueue)
            .to(clickLoggedExchange)
            .with(ClickLoggedEvent.ROUTING_KEY)

    companion object {
        const val QUEUE = "urlshortener.clicks.click-logged"
    }
}

@Component
@Profile("broker")
class ClickLoggedBrokerListener(
    private val recordClick: RecordClick,
) {
    @RabbitListener(queues = [ClickLoggedBrokerConfiguration.QUEUE])
    fun on(event: ClickLoggedEvent) {
        recordClick.record(event)
    }
}
