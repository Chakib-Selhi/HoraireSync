# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the Maven wrapper (tests run in CI) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests -Djacoco.skip=true package \
    && cp target/horairesync-backend-*.jar app.jar

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S horairesync && adduser -S -u 10001 -G horairesync horairesync
COPY --from=build /app/app.jar app.jar
USER horairesync
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
