package es.unizar.urlshortener.links.adapters.cache

import es.unizar.urlshortener.links.application.RedirectCache
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

@Component
@Profile("!docker")
class InMemoryRedirectCache : RedirectCache {
    private val targets = ConcurrentHashMap<String, String>()

    override fun findTarget(hash: String): Optional<String> = Optional.ofNullable(targets[hash])

    override fun putTarget(
        hash: String,
        target: String,
    ) {
        targets[hash] = target
    }

    override fun evict(hash: String) {
        targets.remove(hash)
    }
}
