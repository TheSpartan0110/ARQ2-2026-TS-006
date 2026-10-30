FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Install common library into local maven repo
COPY common ./common
RUN mvn -f common/pom.xml clean install -DskipTests

# Build identity service package
COPY identity ./identity
RUN mvn -f identity/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S eatsnow && adduser -S eatsnow -G eatsnow
USER eatsnow

COPY --from=builder /workspace/identity/target/*.jar app.jar

EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
