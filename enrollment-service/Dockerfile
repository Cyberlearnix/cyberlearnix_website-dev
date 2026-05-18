# Run stage — JAR pre-built locally via ./gradlew bootJar
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
# curl is required for HEALTHCHECK probes
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY enrollment-service/build/libs/ /tmp/jars/
RUN find /tmp/jars -name "*.jar" ! -name "*plain*" -exec cp {} /app/app.jar \; && rm -rf /tmp/jars

EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f https://cyberlearnix.com/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
