package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.nio.charset.StandardCharsets.UTF_8

@Configuration
@Profile("docker")
class RedisInvalidationConfiguration {
    @Bean
    fun redirectCacheInvalidationListener(
        connectionFactory: RedisConnectionFactory,
        redirectCache: RedirectCache,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(
            MessageListener { message, _ -> redirectCache.evict(message.body.toString(UTF_8)) },
            ChannelTopic(RedisShortUrlUpdatedListener.INVALIDATION_CHANNEL),
        )
        return container
    }
}
