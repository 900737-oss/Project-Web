package es.unizar.urlshortener.preview.adapters.fetch

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class OpenGraphFetchConfiguration {
    @Bean
    fun openGraphCircuitBreaker(): CircuitBreaker =
        CircuitBreaker.of(
            "openGraph",
            CircuitBreakerConfig
                .custom()
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build(),
        )
}
