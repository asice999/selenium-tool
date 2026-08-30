FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM selenium/standalone-chrome:latest

USER root
WORKDIR /app

ENV TZ=Asia/Shanghai

COPY --from=build /build/target/selenium-tool-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 7900

ENTRYPOINT ["/bin/bash", "-lc", "/opt/bin/entry_point.sh >/tmp/selenium-base.log 2>&1 & exec java -jar /app/app.jar"]
