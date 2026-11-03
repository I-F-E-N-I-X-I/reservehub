FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system reservehub && useradd --system --gid reservehub reservehub
COPY --from=build /app/target/reservehub-1.0.0.jar app.jar
USER reservehub
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
