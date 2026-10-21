FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/urlshortener.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
