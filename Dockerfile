FROM maven:3.5.3-jdk-10-slim as build

WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -q
RUN pwd
RUN ls /app/target

FROM openjdk:10.0.1-10-jre-slim

WORKDIR /app
EXPOSE 8080
ENV STORE_ENABLED=true
ENV WORKER_ENABLED=true
COPY --from=build /app/target/goldcar-alpakka-kafka-microservice-0.0.1-SNAPSHOT.jar /app

CMD ["java", "-jar", "goldcar-alpakka-kafka-microservice-0.0.1-SNAPSHOT.jar"]
