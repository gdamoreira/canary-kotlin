# Multi-stage Docker build for Canary Kotlin Server
FROM gradle:8.12-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/canary-kotlin /app
EXPOSE 7171 7172
ENTRYPOINT ["/app/bin/canary-kotlin"]
