FROM node:22-alpine AS web-build
WORKDIR /webapp
COPY webapp/package*.json ./
RUN npm ci
COPY webapp ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=web-build /src/main/resources/static/app ./src/main/resources/static/app
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/frontend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
