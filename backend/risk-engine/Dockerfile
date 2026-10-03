# ==============================================================================
# Multi-Stage Dockerfile for CooBS risk-engine
# Java 21 / Eclipse Temurin Alpine Base / Multi-Stage Maven Build
# Reference: docs/ENTERPRISE_ARCHITECTURE_DEFENSE_DOSSIER.md
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Executable JAR with Maven
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy POM and download dependencies for layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source and compile application
COPY src ./src
RUN mvn clean package -DskipTests -B && \
    cp target/*-SNAPSHOT.jar target/app.jar || cp target/*.jar target/app.jar

# ------------------------------------------------------------------------------
# Stage 2: Minimal Distroless Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a non-privileged system user for banking security compliance
RUN addgroup -S banking && adduser -S risk -G banking

COPY --from=builder /workspace/target/app.jar ./app.jar

# Change ownership to non-root user
RUN chown -R risk:banking /app
USER risk:banking

# Environment configuration defaults
ENV SERVER_PORT=8092 \
    JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8092

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8092/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
