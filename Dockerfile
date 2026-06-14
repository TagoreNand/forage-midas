# Midas Core - multi-stage, reproducible, small, non-root image (Roadmap section 5.1).

# ---------- Stage 1: build ----------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system midas && useradd --system --gid midas midas \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/*.jar app.jar
USER midas

# Balance API (33400) + actuator/metrics (8081)
EXPOSE 33400 8081

# Container-aware JVM flags; enable virtual threads once on Java 21.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENV OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4318/v1/traces"

# Liveness/readiness for orchestrators that honour Docker healthchecks.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
