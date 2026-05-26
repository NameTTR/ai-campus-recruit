FROM docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17 AS build

ARG SERVICE
WORKDIR /workspace
COPY settings.xml.example ./settings.xml
COPY pom.xml ./pom.xml
COPY common ./common
COPY ${SERVICE} ./${SERVICE}
RUN mvn -s settings.xml -pl ${SERVICE} -am clean package -DskipTests

FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre

ARG SERVICE
ENV JAVA_OPTS=""
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

