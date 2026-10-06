FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY auth/pom.xml auth/
RUN mvn -B dependency:go-offline -f auth/pom.xml
COPY auth/src/ auth/src/
RUN mvn -B clean package -DskipTests -f auth/pom.xml

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /build/auth/target/the-commit-crew-auth-0.1.0-exec.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]