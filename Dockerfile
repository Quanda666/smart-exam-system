# Multi-stage build for Smart Exam System (Single Container Deployment)
# Optimized for Railway, Render, Fly.io, Heroku and other cloud platforms

# Stage 1: Build Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy package files and install dependencies with clean install
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --omit=dev --ignore-scripts || npm install --production

# Copy source and build (Vue output goes to dist/)
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Backend with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /app/backend

# Copy Maven POM and pre-fetch dependencies (leverages Docker layer caching)
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B

# Copy backend source
COPY backend/src ./src

# Copy built frontend resources from Stage 1 into Spring Boot's static resources directory
COPY --from=frontend-builder /app/frontend/dist/ /app/backend/src/main/resources/static/

# Package the Spring Boot application (including static frontend web resources)
# Force copy resources to target classes directory before packaging, ensuring they are included in the JAR.
RUN mvn resources:resources
RUN mvn package -DskipTests -B

# Stage 3: Production Runner (Minimal JRE Image)
FROM eclipse-temurin:17-jre-alpine AS runner

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy the packaged jar with proper ownership
COPY --from=backend-builder --chown=appuser:appgroup /app/backend/target/smart-exam-backend-*.jar ./app.jar

# Switch to non-root user
USER appuser

# Expose port (Railway/Render/Fly.io will use PORT env variable)
EXPOSE 8080

# Configure JVM for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/api/health || exit 1

# Execute the jar with optimized JVM settings
CMD java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar
