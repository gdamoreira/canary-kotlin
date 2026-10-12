# Multi-stage Docker build for Canary Kotlin Server on Java 25 & Gradle 9
FROM gradle:9.8.1-jdk25 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/canary-kotlin /app
EXPOSE 7171 7172
ENTRYPOINT ["/app/bin/canary-kotlin"]
