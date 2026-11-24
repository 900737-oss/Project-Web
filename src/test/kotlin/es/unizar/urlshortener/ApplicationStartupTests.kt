package es.unizar.urlshortener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.test.annotation.DirtiesContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    args = ["--spring.application.name=main-smoke"],
    properties = ["spring.datasource.url=jdbc:hsqldb:mem:startup-smoke"],
)
@DirtiesContext
class ApplicationStartupTests {
    @Autowired
    lateinit var context: WebServerApplicationContext

    @Test
    fun `main starts an HTTP application with command line configuration`() {
        assertEquals("main-smoke", context.environment.getProperty("spring.application.name"))
        val port = requireNotNull(context.webServer).port
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(5))
                .build()
        HttpClient.newHttpClient().use { client ->
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
        }
    }
}
