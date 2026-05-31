# syntax=docker/dockerfile:1.4
# =============================================================================
# Cyberlearnix — Production Multi-Stage Dockerfile
# Usage: docker build --build-arg SERVICE_NAME=user-service -t user-service:latest .
# All services built from project root to satisfy shared-lib dependency.
# Java 21 | Gradle 8 | eclipse-temurin (JRE-only runtime)
# =============================================================================

ARG JAVA_VERSION=21
ARG GRADLE_IMAGE=gradle:8.14-jdk21

# ── Stage 1: Dependency cache ────────────────────────────────────────────────
FROM ${GRADLE_IMAGE} AS deps
WORKDIR /workspace

# Copy only Gradle wrapper and build scripts — layer cached until deps change
COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle .
COPY build.gradle .

# Copy all subproject build.gradle files to resolve dependency graph
COPY shared-lib/build.gradle        shared-lib/
COPY gateway-service/build.gradle   gateway-service/
COPY user-service/build.gradle      user-service/
COPY course-service/build.gradle    course-service/
COPY enrollment-service/build.gradle enrollment-service/
COPY notification-service/build.gradle notification-service/
COPY shop-service/build.gradle      shop-service/
COPY form-service/build.gradle      form-service/
COPY admin-service/build.gradle     admin-service/
COPY cms-service/build.gradle       cms-service/
COPY instructor-service/build.gradle instructor-service/
COPY attendance-service/build.gradle attendance-service/
COPY lab-service/build.gradle       lab-service/

# Warm up the Gradle dependency cache (tolerates resolution failures on stubs)
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

# ── Stage 2: Build ────────────────────────────────────────────────────────────
FROM deps AS builder
ARG SERVICE_NAME

# shared-lib must build first — all services depend on it
COPY shared-lib/src/ shared-lib/src/
RUN gradle :shared-lib:build --no-daemon -x test

# Build the target service
COPY ${SERVICE_NAME}/src/ ${SERVICE_NAME}/src/
RUN gradle :${SERVICE_NAME}:bootJar --no-daemon -x test \
    && find ${SERVICE_NAME}/build/libs -name "*.jar" ! -name "*plain*" \
       -exec cp {} /app.jar \;

# ── Stage 3: Runtime (minimal JRE) ────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# OCI image labels
ARG SERVICE_NAME
ARG BUILD_DATE
ARG GIT_SHA
LABEL org.opencontainers.image.title="${SERVICE_NAME}" \
      org.opencontainers.image.source="https://github.com/cyberlearnix" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.vendor="Cyberlearnix"

# Security: drop root — run as unprivileged user 1001
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --no-create-home --shell /bin/false appuser

WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /app.jar app.jar

# /tmp writable by appuser for Spring Boot temp files
RUN mkdir -p /tmp && chown appuser:appgroup /tmp

USER appuser

# Container-aware JVM: respect cgroup memory/CPU limits, crash on OOM
ENV JAVA_TOOL_OPTIONS="\
 -XX:+UseContainerSupport \
 -XX:MaxRAMPercentage=75.0 \
 -XX:+ExitOnOutOfMemoryError \
 -XX:+UseG1GC \
 -Djava.security.egd=file:/dev/./urandom \
 -Dspring.output.ansi.enabled=NEVER"

EXPOSE 8080

# Dockerfile-level health check (also defined in Helm probes)
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider \
        http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
