FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Install shared libraries into the local maven repo
COPY common ./common
RUN mvn -f common/pom.xml clean install -DskipTests
COPY messaging ./messaging
RUN mvn -f messaging/pom.xml clean install -DskipTests

# Build payments service package
COPY payments ./payments
RUN mvn -f payments/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S eatsnow && adduser -S eatsnow -G eatsnow
USER eatsnow

COPY --from=builder /workspace/payments/target/*.jar app.jar

EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
