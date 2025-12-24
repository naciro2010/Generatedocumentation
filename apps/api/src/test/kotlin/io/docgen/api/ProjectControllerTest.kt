package io.docgen.api

import io.docgen.api.controller.ImportProjectRequest
import io.docgen.core.model.ProjectStatus
import io.docgen.core.repository.ProjectRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var projectRepository: ProjectRepository

    private val objectMapper = jacksonObjectMapper()

    companion object {
        @Container
        val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `should import project via git url`() {
        val request = ImportProjectRequest(
            name = "test-project",
            gitUrl = "https://github.com/example/repo"
        )

        mockMvc.perform(
            post("/v1/projects/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.projectId").exists())
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `should get project status`() {
        // Create a test project first
        val request = ImportProjectRequest(
            name = "status-test-project",
            gitUrl = "https://github.com/example/repo"
        )

        val response = mockMvc.perform(
            post("/v1/projects/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andReturn()

        val jsonResponse = objectMapper.readTree(response.response.contentAsString)
        val projectId = jsonResponse.get("projectId").asText()

        // Get project status
        mockMvc.perform(get("/v1/projects/$projectId/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projectId").value(projectId))
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.progress").exists())
    }

    @Test
    fun `should list all projects`() {
        mockMvc.perform(get("/v1/projects"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }
}
