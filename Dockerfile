# build
FROM maven:3.6.3-openjdk-11 AS MAVEN_BUILD

WORKDIR /build/
COPY pom.xml /build/
COPY src /build/src/

RUN mvn -q install -Dmaven.test.skip=true

# runtime
FROM openjdk:16-jdk-alpine
VOLUME /tmp

WORKDIR /app
COPY --from=MAVEN_BUILD /build/target/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app.jar"]
