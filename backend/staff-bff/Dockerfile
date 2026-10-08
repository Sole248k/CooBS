# ==============================================================================
# Dockerfile for CooBS staff-bff
# Java 21 / Eclipse Temurin Alpine Base Runtime
# ==============================================================================

FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create a non-privileged system user for banking security compliance
RUN addgroup -S banking && adduser -S staffbff -G banking

COPY target/staff-bff-*.jar ./app.jar

# Change ownership to non-root user
RUN chown -R staffbff:banking /app
USER staffbff:banking

# Environment configuration defaults
ENV SERVER_PORT=8095 \
    JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8095

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8095/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
