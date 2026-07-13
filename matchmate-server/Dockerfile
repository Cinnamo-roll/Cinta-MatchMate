# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="MatchMate Server" \
      org.opencontainers.image.source="https://github.com/Cinnamo-roll/Cinta-MatchMate" \
      org.opencontainers.image.authors="CintaOvO (https://github.com/Cinnamo-roll)" \
      org.opencontainers.image.licenses="Apache-2.0"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
