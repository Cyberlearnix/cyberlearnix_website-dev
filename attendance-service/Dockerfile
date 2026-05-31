# Run stage — initial image push trigger
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY attendance-service/build/libs/ /tmp/jars/
RUN find /tmp/jars -name "*.jar" ! -name "*plain*" -exec cp {} /app/app.jar \; && rm -rf /tmp/jars
EXPOSE 8092
HEALTHCHECK --interval=30s --timeout=10s --start_period=60s --retries=3 \
  CMD curl -f http://localhost:8092/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
