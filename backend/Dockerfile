# Backend-only Dockerfile for Smart Exam System
# Use this for separate backend deployment or development

# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy Maven POM and pre-fetch dependencies (leverages Docker layer caching)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests -B

# Stage 2: Production Runtime
FROM eclipse-temurin:17-jre-alpine AS runner

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy the packaged jar with proper ownership
COPY --from=builder --chown=appuser:appgroup /app/target/smart-exam-backend-*.jar ./app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Configure JVM for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/api/health || exit 1

# Run application
CMD java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar
