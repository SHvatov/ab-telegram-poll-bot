# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS builder
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system pollbot && useradd --system --no-create-home --gid pollbot pollbot
WORKDIR /app
COPY --from=builder --chown=pollbot:pollbot /build/target/poll-bot-*.jar app.jar
USER pollbot
ENTRYPOINT ["java", "-jar", "app.jar"]
