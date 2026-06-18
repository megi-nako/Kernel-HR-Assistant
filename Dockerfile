# ── Stage 1: Build React frontend ──────────────────────────────────────────
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci --silent
COPY frontend/ ./
# VITE_API_BASE left empty → all /api calls go to same origin (no CORS friction)
RUN npm run build

# ── Stage 2: Build Spring Boot JAR (with frontend static files embedded) ───
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -q
COPY backend/src ./src
# Embed the Vite output into Spring Boot's static resource path so the JAR
# serves the React app directly — no separate frontend service needed.
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -q

# ── Stage 3: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Include mock profiles so AUTH_MODE=mock works in the container
COPY data/mock_profiles/ ./data/mock_profiles/
EXPOSE 8080
ENV MOCK_PROFILE_PATH=/app/data/mock_profiles/
ENTRYPOINT ["java", "-jar", "app.jar"]
