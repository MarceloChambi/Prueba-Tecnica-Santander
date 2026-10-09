FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY ms-core/pom.xml ms-core/pom.xml
COPY ms-business/pom.xml ms-business/pom.xml
COPY ms-core/src ms-core/src
COPY ms-business/src ms-business/src
ARG MODULE
RUN mvn -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG MODULE
COPY --from=build /app/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
