FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV SERVER_PORT=9999
ENV FILE_UPLOAD_PATH=/app/uploads

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 9999

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
