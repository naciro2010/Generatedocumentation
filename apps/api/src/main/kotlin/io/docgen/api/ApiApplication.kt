package io.docgen.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["io.docgen"])
@EntityScan("io.docgen.core.model")
@EnableJpaRepositories("io.docgen.core.repository")
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
