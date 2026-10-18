package es.unizar.urlshortener.links.adapters.broker

import es.unizar.urlshortener.links.ShortUrlUpdatedEvent
import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Configuration
@Profile("broker")
class RedirectInvalidationBrokerConfiguration {
    @Bean
    fun shortUrlUpdatedExchange(): TopicExchange = TopicExchange(ShortUrlUpdatedEvent.EXCHANGE, true, false)

    @Bean
    fun redirectInvalidationQueue(): Queue = Queue(QUEUE, true)

    @Bean
    fun redirectInvalidationBinding(
        redirectInvalidationQueue: Queue,
        shortUrlUpdatedExchange: TopicExchange,
    ): Binding =
        BindingBuilder
            .bind(redirectInvalidationQueue)
            .to(shortUrlUpdatedExchange)
            .with(ShortUrlUpdatedEvent.ROUTING_KEY)

    @Bean
    fun rabbitListenerContainerFactory(
        connectionFactory: ConnectionFactory,
        jsonMapper: JsonMapper,
    ): SimpleRabbitListenerContainerFactory =
        SimpleRabbitListenerContainerFactory().apply {
            setConnectionFactory(connectionFactory)
            setMessageConverter(JacksonJsonMessageConverter(jsonMapper))
        }

    @Bean
    fun persistentShortUrlUpdatedDelivery(): RabbitTemplateCustomizer =
        RabbitTemplateCustomizer { template: RabbitTemplate ->
            template.addBeforePublishPostProcessors(
                MessagePostProcessor { message ->
                    message.messageProperties.deliveryMode = MessageDeliveryMode.PERSISTENT
                    message
                },
            )
        }

    companion object {
        const val QUEUE = "urlshortener.links.redirect-invalidation"
    }
}

@Component
@Profile("broker")
class RedirectInvalidationBrokerListener(
    private val redirectCache: RedirectCache,
) {
    @RabbitListener(queues = [RedirectInvalidationBrokerConfiguration.QUEUE])
    fun onUpdated(event: ShortUrlUpdatedEvent) {
        redirectCache.evict(event.hash)
    }
}
