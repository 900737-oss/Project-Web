package es.unizar.urlshortener.links

import es.unizar.urlshortener.links.adapters.cache.InMemoryRedirectCache
import es.unizar.urlshortener.links.application.CreateShortUrl
import es.unizar.urlshortener.links.application.DeactivateShortUrl
import es.unizar.urlshortener.links.application.LinkGoneException
import es.unizar.urlshortener.links.application.RedirectCache
import es.unizar.urlshortener.links.application.RedirectShortUrl
import es.unizar.urlshortener.links.application.Redirection
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest
@Import(RedirectCacheConcurrencyTests.CacheConfiguration::class)
class RedirectCacheConcurrencyTests {
    @Autowired
    lateinit var createShortUrl: CreateShortUrl

    @Autowired
    lateinit var redirectShortUrl: RedirectShortUrl

    @Autowired
    lateinit var deactivateShortUrl: DeactivateShortUrl

    @Autowired
    lateinit var cache: PausingRedirectCache

    @Autowired
    lateinit var transactions: PlatformTransactionManager

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `deactivation waits for an earlier cache fill and then evicts it`() {
        val hash = createShortUrl.create(TARGET).hash
        val write = cache.pauseNextPut(hash)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val redirect = workers.submit<Redirection> { redirectShortUrl.redirect(hash) }
            write.awaitStarted()
            val deactivation = workers.submit<Unit> { deactivateShortUrl.deactivate(hash) }

            assertWaiting(deactivation)
            write.release()

            assertEquals(TARGET, redirect.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).target)
            deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertStopped(hash)
        } finally {
            write.release()
            workers.shutdownNow()
        }
    }

    @Test
    fun `a cold redirect waits for pending deactivation and observes its committed state`() {
        val hash = createShortUrl.create(TARGET).hash
        val updated = CountDownLatch(1)
        val commit = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        try {
            val deactivation = workers.submit { deactivateBeforeCommit(hash, updated, commit) }
            assertTrue(updated.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val redirect = workers.submit<Redirection> { redirectShortUrl.redirect(hash) }

            assertWaiting(redirect)
            commit.countDown()

            deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val failure = assertThrows(ExecutionException::class.java) { redirect.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            assertInstanceOf(LinkGoneException::class.java, failure.cause)
            assertStopped(hash)
        } finally {
            commit.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun `repeated deactivation evicts while the first after commit eviction is pending`() {
        val hash = createShortUrl.create(TARGET).hash
        redirectShortUrl.redirect(hash)
        assertTrue(cache.findTarget(hash).isPresent)
        val eviction = cache.pauseNextEviction(hash)
        val workers = Executors.newSingleThreadExecutor()
        try {
            val firstDeactivation = workers.submit<Unit> { deactivateShortUrl.deactivate(hash) }
            eviction.awaitStarted()

            deactivateShortUrl.deactivate(hash)
            assertStopped(hash)

            eviction.release()
            firstDeactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            eviction.release()
            workers.shutdownNow()
        }
    }

    private fun deactivateBeforeCommit(
        hash: String,
        updated: CountDownLatch,
        commit: CountDownLatch,
    ) {
        TransactionTemplate(transactions).executeWithoutResult {
            deactivateShortUrl.deactivate(hash)
            entityManager.flush()
            updated.countDown()
            check(commit.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "test did not release the deactivation transaction" }
        }
    }

    private fun assertWaiting(future: Future<*>) {
        assertThrows(TimeoutException::class.java) { future.get(1, TimeUnit.SECONDS) }
    }

    private fun assertStopped(hash: String) {
        assertTrue(cache.findTarget(hash).isEmpty)
        assertThrows(LinkGoneException::class.java) { redirectShortUrl.redirect(hash) }
        assertTrue(cache.findTarget(hash).isEmpty)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class CacheConfiguration {
        @Bean
        @Primary
        fun pausingRedirectCache(delegate: InMemoryRedirectCache): PausingRedirectCache = PausingRedirectCache(delegate)
    }

    class PausingRedirectCache(
        private val delegate: InMemoryRedirectCache,
    ) : RedirectCache {
        private val nextWrite = AtomicReference<PausedCacheOperation?>()
        private val nextEviction = AtomicReference<PausedCacheOperation?>()

        fun pauseNextPut(hash: String): PausedCacheOperation = PausedCacheOperation(hash).also { nextWrite.set(it) }

        fun pauseNextEviction(hash: String): PausedCacheOperation = PausedCacheOperation(hash).also { nextEviction.set(it) }

        override fun findTarget(hash: String): Optional<String> = delegate.findTarget(hash)

        override fun putTarget(
            hash: String,
            target: String,
        ) {
            pauseIfRequested(hash, nextWrite)
            delegate.putTarget(hash, target)
        }

        override fun evict(hash: String) {
            pauseIfRequested(hash, nextEviction)
            delegate.evict(hash)
        }

        private fun pauseIfRequested(
            hash: String,
            next: AtomicReference<PausedCacheOperation?>,
        ) {
            val operation = next.get()
            if (operation != null && operation.hash == hash && next.compareAndSet(operation, null)) {
                operation.awaitRelease()
            }
        }
    }

    class PausedCacheOperation(
        val hash: String,
    ) {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun awaitStarted() {
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        fun awaitRelease() {
            started.countDown()
            check(released.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "test did not release the cache operation" }
        }

        fun release() = released.countDown()
    }

    private companion object {
        const val TARGET = "https://example.com/redirect-cache-race"
        const val TIMEOUT_SECONDS = 15L
    }
}
