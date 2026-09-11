FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /app/target/foodnow-demo-1.0.0.jar app.jar

RUN mkdir -p /app/data && chown -R 10001:10001 /app/data
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 4242
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
