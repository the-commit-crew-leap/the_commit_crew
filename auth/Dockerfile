FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY auth/pom.xml .
RUN mvn -B dependency:go-offline -f pom.xml
COPY auth/src/ src/
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /build/target/the-commit-crew-auth-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]