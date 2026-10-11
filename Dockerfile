# Stage 1: build the executable jar with the Gradle wrapper of the project
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# the wrapper and the build script are copied first, so the downloaded dependencies stay in the Docker cache
# until build.gradle changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar

# Stage 2: the runtime image contains only the JRE and the application
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl is needed only for the health check below; the application runs as a user without root rights
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home shopsphere
USER shopsphere

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
