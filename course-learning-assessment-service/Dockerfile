FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN apk add --no-cache wget \
    && addgroup -S spring \
    && adduser -S spring -G spring
COPY --chown=spring:spring target/course-learning-assessment-service-*.jar app.jar
USER spring
EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=30s CMD wget -q -O - http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
