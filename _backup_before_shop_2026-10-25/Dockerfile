# syntax=docker/dockerfile:1
# =====================================================================
#  ArtistikCity web application (Spring Boot + React bundle)
#  Stage 1 builds the jar with Maven, stage 2 runs it on a slim JRE as a non-root user.
#  The same image runs locally (docker compose) and on AWS (EC2, see docs/AWS-DEPLOY.md).
#  The React bundle (src/main/resources/static/js/app.js) is committed, so no Node build is needed here.
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /src/target/artistikcity-1.0.0.jar /app/app.jar
# schema, seed data and migrations: used by deploy/db-migrate.sh (copied out of the image on every deploy)
COPY database /app/database
COPY deploy /app/deploy
# demo images for local docker compose (mounted as a volume there). On AWS uploads live in S3 instead.
COPY storage /app/storage
RUN mkdir -p /app/logs && chown -R app:app /app/storage /app/logs && chmod 0755 /app/deploy/*.sh
USER app
ENV PORT=8080 \
    STORAGE_DIR=/app/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
