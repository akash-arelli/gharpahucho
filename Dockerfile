# One image: the React app is built, copied into the Spring Boot jar and served by it, so the whole product
# (UI + API + public family pages) lives on a single address. Secrets are NOT baked in; pass them as environment
# variables / host secrets at run time (SARVAM_API_KEY, FLAGSMITH_SERVER_KEY, NUDGEBEE_EVENT_WEBHOOK_URL, ...).

FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
# The lockfile was produced on Windows and lacks some Linux-only optional packages, so `npm ci` rejects it.
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# Hosts such as Hugging Face Spaces run containers as UID 1000, which this base image already provides.
USER 1000
WORKDIR /app
COPY --from=api /api/target/gharpahucho-backend.jar app.jar

# "demo" = in-memory database seeded with demo travellers (resets on restart). Use a Postgres URL and the default
# profile for anything that must keep user data.
ENV PORT=7860 \
    SPRING_PROFILES_ACTIVE=demo \
    GP_ENVIRONMENT=demo \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "app.jar"]
