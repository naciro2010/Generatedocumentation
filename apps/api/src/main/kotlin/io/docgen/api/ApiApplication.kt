package io.docgen.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["io.docgen"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
