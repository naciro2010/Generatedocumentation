plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":libs:core"))

    // Template engine (for Markdown generation)
    implementation("org.freemarker:freemarker:2.3.32")

    // Markdown processing
    implementation("org.commonmark:commonmark:0.21.0")

    // PDF generation
    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.10")

    // YAML for MkDocs config
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
