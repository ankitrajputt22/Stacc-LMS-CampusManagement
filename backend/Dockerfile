FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home stacc
COPY --from=build /build/target/stacc-backend-*.jar app.jar
USER stacc

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
