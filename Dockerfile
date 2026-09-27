# syntax=docker/dockerfile:1
# =====================================================================
#  ArtistikCity web application (Spring Boot + React bundle)
#  Stage 1 builds the jar with Maven, stage 2 runs it on a slim JRE.
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/artistikcity-1.0.0.jar /app/app.jar
COPY database /app/database
# demo images (course photos, teachers, ...). Mounted as a volume in docker-compose so uploads persist.
COPY storage /app/storage
ENV PORT=8080 \
    STORAGE_DIR=/app/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
