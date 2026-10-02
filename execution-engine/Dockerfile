FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they're cached across source-only changes
COPY pom.xml .
COPY execution-engine/pom.xml execution-engine/
RUN mvn -B dependency:go-offline -f execution-engine/pom.xml

COPY execution-engine/src ./execution-engine/src
RUN mvn -B clean package -DskipTests -f execution-engine/pom.xml

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S engine && adduser -S engine -G engine
WORKDIR /opt/execution-engine
COPY --from=build /build/execution-engine/target/execution-engine-0.1.0.jar app.jar
USER engine

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]