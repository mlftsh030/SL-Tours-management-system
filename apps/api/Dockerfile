FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# The cache mount keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:resolve
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# Run as an unprivileged user; uploads live on a mounted private volume, never in the image.
RUN useradd --system --uid 10001 --home-dir /app sltours \
    && mkdir -p /var/lib/sltours/media && chown -R sltours /var/lib/sltours
WORKDIR /app
COPY --from=build /workspace/target/sl-tours-api-0.1.0-SNAPSHOT.jar app.jar
ENV SLTOURS_MEDIA_ROOT=/var/lib/sltours/media
VOLUME ["/var/lib/sltours/media"]
USER sltours
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
