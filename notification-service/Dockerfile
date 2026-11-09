FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/target/notification-service-*.jar app.jar
USER spring
EXPOSE 8087
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=30s CMD wget -q -O - http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]

