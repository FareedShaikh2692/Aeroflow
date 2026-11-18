# syntax=docker/dockerfile:1.7
# One image recipe for every service:  docker build --build-arg SERVICE=booking-service -t aeroflow/booking-service backend
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE=checkin-service
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY auth-service/pom.xml auth-service/
COPY flight-service/pom.xml flight-service/
COPY passenger-service/pom.xml passenger-service/
COPY booking-service/pom.xml booking-service/
COPY checkin-service/pom.xml checkin-service/
COPY notification-service/pom.xml notification-service/
COPY reporting-service/pom.xml reporting-service/
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl ${SERVICE} -am dependency:go-offline -DskipTests || true
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl ${SERVICE} -am package -DskipTests \
 && java -Djarmode=layertools -jar ${SERVICE}/target/app.jar extract --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S aeroflow && adduser -S aeroflow -G aeroflow && apk add --no-cache wget
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER aeroflow
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SPRING_PROFILES_ACTIVE=json-logs PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health/liveness" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
