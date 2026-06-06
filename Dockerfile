# Multi-stage build for Smart Exam System (Single Container Deployment)

# Stage 1: Build Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy package files and install dependencies
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install

# Copy source and build (Vue output goes to dist/)
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Backend with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /app/backend

# Copy Maven POM and pre-fetch dependencies
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B

# Copy backend source
COPY backend/src ./src

# Copy built frontend resources from Stage 1 into Spring Boot's static resources directory
COPY --from=frontend-builder /app/frontend/dist/ /app/backend/src/main/resources/static/

# Package the Spring Boot application (including static frontend web resources)
RUN mvn package -DskipTests -B

# Stage 3: Runner
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Ensure we run in production mode
ENV SPRING_PROFILES_ACTIVE=prod

# Copy the packaged jar
COPY --from=backend-builder /app/backend/target/smart-exam-backend-*.jar ./app.jar

# Expose port (Railway or environment should define PORT, defaults to 8080)
EXPOSE 8080

# Execute the jar
CMD ["java", "-jar", "app.jar"]
