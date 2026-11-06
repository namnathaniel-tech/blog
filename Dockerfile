# Single production image: React build is baked into Spring Boot's static/ folder.
# Build:  docker build -t blog .
# Run:    docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod blog
# Tests are NOT run here; CI runs them (.github/workflows/ci.yml).

FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./mvnw -q -DskipTests package && cp target/backend-*.jar /app/app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 blog
USER blog
WORKDIR /app
COPY --from=backend /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
