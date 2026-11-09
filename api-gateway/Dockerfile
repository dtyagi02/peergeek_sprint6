FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring
COPY --chown=spring:spring target/api-gateway-*.jar app.jar
USER spring
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=20s CMD wget -q -O - http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]

