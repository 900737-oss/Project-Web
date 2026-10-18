FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/urlshortener.jar app.jar
EXPOSE 8080
# docker compose --profile broker adds the rabbit host. The shell then enables the broker profile.
ENTRYPOINT ["sh", "-c", "profiles=docker; if getent hosts rabbit >/dev/null 2>&1; then profiles=docker,broker; fi; exec java -jar /app/app.jar --spring.profiles.active=$profiles"]
