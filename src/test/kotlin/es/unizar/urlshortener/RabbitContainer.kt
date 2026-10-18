package es.unizar.urlshortener

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.rabbitmq.RabbitMQContainer

@TestConfiguration(proxyBeanMethods = false)
class RabbitContainer {
    @Bean
    @ServiceConnection
    fun rabbit() = RabbitMQContainer("rabbitmq:3.13-management-alpine")
}
