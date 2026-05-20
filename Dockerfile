FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .

RUN --mount=type=secret,id=maven_settings,dst=/root/.m2/settings.xml \
    mvn -B dependency:go-offline

COPY src ./src

RUN --mount=type=secret,id=maven_settings,dst=/root/.m2/settings.xml \
    mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]