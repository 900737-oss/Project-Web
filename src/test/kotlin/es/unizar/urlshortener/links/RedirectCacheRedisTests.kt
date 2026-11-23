package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.adapters.cache.RedisInvalidationConfiguration
import es.unizar.urlshortener.links.adapters.cache.RedisRedirectCache
import es.unizar.urlshortener.links.adapters.cache.RedisShortUrlUpdatedListener
import es.unizar.urlshortener.links.application.RedirectCache
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@Testcontainers(disabledWithoutDocker = true)
class RedirectCacheRedisTests {
    private lateinit var connections: LettuceConnectionFactory
    private lateinit var redis: StringRedisTemplate

    @BeforeEach
    fun connect() {
        connections = LettuceConnectionFactory(redisServer.host, redisServer.getMappedPort(6379))
        connections.afterPropertiesSet()
        connections.start()
        redis = StringRedisTemplate(connections)
    }

    @AfterEach
    fun disconnect() {
        connections.destroy()
    }

    @Test
    fun `replicas share targets and eviction without waiting for expiry`() {
        val writer = RedisRedirectCache(redis, Duration.ofMinutes(30))
        val reader = RedisRedirectCache(StringRedisTemplate(connections), Duration.ofMinutes(30))
        val hash = UUID.randomUUID().toString()
        assertTrue(reader.findTarget(hash).isEmpty)

        writer.putTarget(hash, TARGET)

        assertEquals(TARGET, reader.findTarget(hash).orElseThrow())
        val ttl = checkNotNull(redis.getExpire("urlshortener:redirect:$hash", TimeUnit.MILLISECONDS))
        assertTrue(ttl in 1..Duration.ofMinutes(30).toMillis())

        reader.evict(hash)

        assertTrue(writer.findTarget(hash).isEmpty)
    }

    @Test
    fun `redirect entries expire after their configured ttl`() {
        val cache = RedisRedirectCache(redis, Duration.ofSeconds(1))
        val hash = UUID.randomUUID().toString()

        cache.putTarget(hash, TARGET)
        assertEquals(TARGET, cache.findTarget(hash).orElseThrow())

        await().atMost(Duration.ofSeconds(5)).until { cache.findTarget(hash).isEmpty }
    }

    @Test
    fun `a pubsub notification invalidates only its hash`() {
        val cache = RedisRedirectCache(redis, Duration.ofMinutes(30))
        val hash = UUID.randomUUID().toString()
        val otherHash = UUID.randomUUID().toString()
        cache.putTarget(hash, TARGET)
        cache.putTarget(otherHash, TARGET)
        val subscriber = RedisInvalidationConfiguration().redirectCacheInvalidationListener(connections, cache)
        try {
            subscriber.afterPropertiesSet()
            subscriber.start()

            assertEquals(1L, redis.convertAndSend(RedisShortUrlUpdatedListener.INVALIDATION_CHANNEL, hash))

            await().atMost(Duration.ofSeconds(5)).until { cache.findTarget(hash).isEmpty }
            assertEquals(TARGET, cache.findTarget(otherHash).orElseThrow())
        } finally {
            subscriber.destroy()
        }
    }

    @Test
    fun `committed updates evict before returning and publish the hash to other replicas`() {
        val cache = RedisRedirectCache(redis, Duration.ofMinutes(30))
        val hash = UUID.randomUUID().toString()
        cache.putTarget(hash, TARGET)
        withTransactionalListener(cache) { events, transactions, messages ->
            TransactionTemplate(transactions).executeWithoutResult {
                events.publishEvent(ShortUrlUpdatedEvent(hash = hash, active = false))
                assertEquals(TARGET, cache.findTarget(hash).orElseThrow())
            }

            assertTrue(cache.findTarget(hash).isEmpty)
            assertEquals(hash, messages.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `rolled back updates and events outside a transaction keep the active cache entry`() {
        val cache = RedisRedirectCache(redis, Duration.ofMinutes(30))
        val hash = UUID.randomUUID().toString()
        cache.putTarget(hash, TARGET)
        withTransactionalListener(cache) { events, transactions, messages ->
            events.publishEvent(ShortUrlUpdatedEvent(hash = hash, active = false))
            assertEquals(TARGET, cache.findTarget(hash).orElseThrow())

            TransactionTemplate(transactions).executeWithoutResult {
                events.publishEvent(ShortUrlUpdatedEvent(hash = hash, active = false))
                it.setRollbackOnly()
            }

            assertEquals(TARGET, cache.findTarget(hash).orElseThrow())
            assertNull(messages.poll(200, TimeUnit.MILLISECONDS))
        }
    }

    private fun withTransactionalListener(
        cache: RedirectCache,
        action: (ApplicationEventPublisher, PlatformTransactionManager, BlockingQueue<String>) -> Unit,
    ) {
        val messages = LinkedBlockingQueue<String>()
        val database = EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.HSQL).build()
        try {
            val subscriber = messageObserver(messages)
            try {
                eventContext(RedisShortUrlUpdatedListener(redis, cache)).use { events ->
                    action(events, DataSourceTransactionManager(database), messages)
                }
            } finally {
                subscriber.destroy()
            }
        } finally {
            database.shutdown()
        }
    }

    private fun eventContext(listener: RedisShortUrlUpdatedListener): AnnotationConfigApplicationContext =
        AnnotationConfigApplicationContext().apply {
            environment.setActiveProfiles("docker")
            register(TransactionEvents::class.java)
            registerBean(RedisShortUrlUpdatedListener::class.java, Supplier { listener })
            try {
                refresh()
            } catch (failure: Exception) {
                runCatching { close() }.onFailure(failure::addSuppressed)
                throw failure
            }
        }

    private fun messageObserver(messages: BlockingQueue<String>): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connections)
            addMessageListener(
                MessageListener { message, _ -> messages.add(message.body.toString(UTF_8)) },
                ChannelTopic(RedisShortUrlUpdatedListener.INVALIDATION_CHANNEL),
            )
            try {
                afterPropertiesSet()
                start()
            } catch (failure: Exception) {
                runCatching { destroy() }.onFailure(failure::addSuppressed)
                throw failure
            }
        }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class TransactionEvents

    private class RedisServer : GenericContainer<RedisServer>("redis:7.4-alpine")

    private companion object {
        const val TARGET = "https://example.com/shared-redirect"

        @Container
        @JvmField
        val redisServer: GenericContainer<*> = RedisServer().withExposedPorts(6379)
    }
}
