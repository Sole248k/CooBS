# ==============================================================================
# Multi-Stage Dockerfile for CooBS Order & Escrow Service
# Java 21 / Eclipse Temurin Alpine Base / Multi-Stage Maven Build
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Executable JAR with Maven
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2"

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B && \
    cp target/*-SNAPSHOT.jar target/app.jar || cp target/*.jar target/app.jar

# ------------------------------------------------------------------------------
# Stage 2: Minimal Distroless Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S banking && adduser -S order -G banking

COPY --from=builder /workspace/target/app.jar ./app.jar

RUN chown -R order:banking /app
USER order:banking

ENV SERVER_PORT=8096 \
    JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8096

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8096/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
