# ==============================================================================
# Build Stage: Su dung Maven 3.9 + Temurin JDK 21 Alpine
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build source code
COPY src ./src
RUN mvn clean package -DskipTests

# ==============================================================================
# Runtime Stage: Su dung Temurin JRE 21 Alpine (~160MB)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tao non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

USER appuser

# Development environment: Không giới hạn RAM, cho phép JVM tự do tận dụng RAM và GC đa luồng (G1GC)
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
