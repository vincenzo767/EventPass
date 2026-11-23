# EventPass production image: builds the React SPA, packages it inside the Spring Boot jar,
# and runs a single same-origin service on a slim JRE as a non-root user.

# ---- 1. Frontend ---------------------------------------------------------------------------
FROM node:22-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. Backend ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist /app/frontend/dist
# Tests run in CI (they need the embedded PostgreSQL binaries); the image build only packages.
RUN mvn -q -B package -DskipTests

# ---- 3. Runtime ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=backend /app/backend/target/eventpass.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true" \
    PORT=8080
EXPOSE 8080
# Health endpoint: /actuator/health (used by the hosting platform's health checks).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
