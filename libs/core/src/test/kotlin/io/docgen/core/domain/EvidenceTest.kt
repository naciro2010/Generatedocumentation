package io.docgen.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvidenceTest {

    @Test
    fun `should create evidence reference`() {
        val evidence = Evidence(
            filePath = "src/main/kotlin/App.kt",
            startLine = 42,
            endLine = 50,
            symbol = "calculateTotal"
        )

        val reference = evidence.toReference()

        assertThat(reference).isEqualTo("src/main/kotlin/App.kt:42-50 (calculateTotal)")
    }

    @Test
    fun `should create evidence reference without end line`() {
        val evidence = Evidence(
            filePath = "src/main/kotlin/App.kt",
            startLine = 42,
            symbol = "calculateTotal"
        )

        val reference = evidence.toReference()

        assertThat(reference).isEqualTo("src/main/kotlin/App.kt:42 (calculateTotal)")
    }

    @Test
    fun `should create certain assertion`() {
        val evidence = Evidence(
            filePath = "test.kt",
            startLine = 1,
            symbol = "test"
        )

        val assertion = Assertion.certain("some value", evidence)

        assertThat(assertion.confidence).isEqualTo(Confidence.CERTAIN)
        assertThat(assertion.evidences).hasSize(1)
        assertThat(assertion.uncertainties).isEmpty()
    }

    @Test
    fun `should create uncertain assertion with questions`() {
        val assertion = Assertion.uncertain(
            "unknown feature",
            listOf("What is the business purpose?", "Who are the users?")
        )

        assertThat(assertion.confidence).isEqualTo(Confidence.INCERTAIN)
        assertThat(assertion.uncertainties).hasSize(2)
        assertThat(assertion.evidences).isEmpty()
    }
}
