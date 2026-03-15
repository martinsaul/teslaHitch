FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/build/libs/teslaHitch-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080 8000
VOLUME /config

ENTRYPOINT ["java", "-jar", "app.jar"]
