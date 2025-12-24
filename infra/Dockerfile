# Multi-stage Dockerfile for universal-doc-generator

# Build stage
FROM gradle:8.11-jdk21-alpine AS build

WORKDIR /workspace

# Copy gradle wrapper and build files
COPY gradle gradle/
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .

# Copy all modules
COPY libs libs/
COPY apps apps/

# Build the specific module
ARG MODULE
RUN ./gradlew :apps:${MODULE}:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

ARG MODULE
ENV MODULE_NAME=${MODULE}

WORKDIR /app

# Install git for cloning repositories
RUN apk add --no-cache git

# Copy the built JAR
COPY --from=build /workspace/apps/${MODULE}/build/libs/*.jar app.jar

# Create storage directory
RUN mkdir -p /app/storage

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
