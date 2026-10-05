# ==============================================================================
# Dockerfile for CooBS transaction-service
# Java 21 / Eclipse Temurin Alpine Base
# ==============================================================================

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a non-privileged system user for banking security compliance
RUN addgroup -S banking && adduser -S txn -G banking

# Copy executable JAR built by Maven
COPY target/*.jar ./app.jar

# Change ownership to non-root user
RUN chown -R txn:banking /app
USER txn:banking

# Environment configuration defaults
ENV SERVER_PORT=8090 \
    JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8090

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
