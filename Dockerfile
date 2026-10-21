FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/urlshortener.jar app.jar
EXPOSE 8080
# `docker compose up` has no rabbit service, so the app stays on the docker profile.
# `docker compose --profile broker up` publishes the name "rabbit"; that adds the broker profile.
ENTRYPOINT ["sh", "-c", "profiles=\"${SPRING_PROFILES_ACTIVE:-docker}\"; if getent hosts rabbit >/dev/null 2>&1; then case \",$profiles,\" in *,broker,*) ;; *) profiles=\"$profiles,broker\";; esac; fi; exec java -jar /app/app.jar --spring.profiles.active=\"$profiles\""]
