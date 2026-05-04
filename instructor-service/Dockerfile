# Build stage
FROM gradle:9.5-jdk21 AS build
WORKDIR /app

# Copy gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy source code
COPY shared-lib shared-lib
COPY instructor-service instructor-service

# Build the instructor-service
RUN gradle :instructor-service:bootJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# curl is required for HEALTHCHECK probes
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/instructor-service/build/libs/*.jar app.jar

EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8091/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
