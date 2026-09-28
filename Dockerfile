FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy parent pom first
COPY pom.xml .

# Copy both modules
COPY auth/ auth/
COPY app/ app/

RUN mvn -B dependency:go-offline -f pom.xml
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /app/app/target/the-commit-crew-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]