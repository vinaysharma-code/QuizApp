FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001:10001
HEALTHCHECK --start-period=90s --interval=15s --timeout=5s --retries=4 CMD curl --fail --silent http://127.0.0.1:${PORT:-8765}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
