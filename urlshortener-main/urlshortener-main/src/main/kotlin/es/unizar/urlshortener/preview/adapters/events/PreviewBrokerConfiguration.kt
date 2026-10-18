package es.unizar.urlshortener.preview.adapters.events

import es.unizar.urlshortener.preview.LINK_PREVIEW_REQUESTED_QUEUE
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.ExchangeBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import tools.jackson.databind.json.JsonMapper

@Configuration
@Profile("broker")
class PreviewBrokerConfiguration {
    @Bean
    fun previewRequestedQueue(): Queue = QueueBuilder.durable(LINK_PREVIEW_REQUESTED_QUEUE).build()

    @Bean
    fun previewRequestedExchange(): DirectExchange =
        ExchangeBuilder.directExchange(LINK_PREVIEW_REQUESTED_QUEUE).durable(true).build()

    @Bean
    fun previewRequestedBinding(
        previewRequestedQueue: Queue,
        previewRequestedExchange: DirectExchange,
    ): Binding =
        BindingBuilder
            .bind(previewRequestedQueue)
            .to(previewRequestedExchange)
            .with(LINK_PREVIEW_REQUESTED_QUEUE)

    @Bean
    fun previewMessageConverter(jsonMapper: JsonMapper): MessageConverter = JacksonJsonMessageConverter(jsonMapper)
}
