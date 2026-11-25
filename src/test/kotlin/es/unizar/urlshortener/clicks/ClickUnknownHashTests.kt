package es.unizar.urlshortener.clicks

import es.unizar.urlshortener.clicks.adapters.persistence.ClickEntity
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Test: un hash desconocido no debe publicar click ni subir contadores
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClickUnknownHashTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `unknown hash returns problem details and does not increase a count`() {
        val unknown = "missing-click-hash"

        mockMvc
            .perform(get("/$unknown"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").isString)

        mockMvc.perform(get("/api/stats/$unknown")).andExpect(status().isNotFound)

        val clickRows =
            entityManager
                .createQuery(
                    "select count(c) from ${ClickEntity::class.java.name} c where c.hash = :hash",
                    Long::class.java,
                ).setParameter("hash", unknown)
                .singleResult

        assertEquals(0L, clickRows)
    }
}
