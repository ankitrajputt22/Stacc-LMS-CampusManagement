# Build stage: Maven with JDK 21 compiles and packages the application.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# The POM is copied first, so the downloaded dependencies stay cached until it changes.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests are run separately with "mvn test". They are skipped here to keep image builds quick.
RUN mvn -B -ntp -DskipTests package

# Runtime stage: only a Java 21 runtime and the packaged application. No source code,
# no build tools, and no credentials. Configuration comes from environment variables.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home stacc
COPY --from=build /build/target/stacc-backend-*.jar app.jar
USER stacc

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
