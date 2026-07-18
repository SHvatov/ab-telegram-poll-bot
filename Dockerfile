# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS builder
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=builder /build/target/poll-bot-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
