FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# The gateway is reactive and does not use the shared servlet libraries
COPY gateway ./gateway
RUN mvn -f gateway/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S eatsnow && adduser -S eatsnow -G eatsnow
USER eatsnow

COPY --from=builder /workspace/gateway/target/*.jar app.jar

EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
