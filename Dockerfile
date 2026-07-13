# --- build ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy just the Gradle wrapper first so the base layer caches.
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

# Copy each sub-module's build.gradle.kts so Gradle can resolve dependencies
# without needing the sources yet. This layer stays cached across source-only
# changes for faster CI.
COPY ledger-domain/build.gradle.kts         ledger-domain/
COPY ledger-application/build.gradle.kts    ledger-application/
COPY ledger-infrastructure/build.gradle.kts ledger-infrastructure/
COPY ledger-app/build.gradle.kts            ledger-app/

RUN chmod +x gradlew && ./gradlew --no-daemon :ledger-app:dependencies 2>&1 | tail -1

# Now copy all sources and build.
COPY ledger-domain/src         ledger-domain/src
COPY ledger-application/src    ledger-application/src
COPY ledger-infrastructure/src ledger-infrastructure/src
COPY ledger-app/src            ledger-app/src

RUN ./gradlew --no-daemon :ledger-app:bootJar -x test

# --- runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /src/ledger-app/build/libs/*.jar app.jar

USER app

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
