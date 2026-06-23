FROM gradle:8-jdk21 AS builder
WORKDIR /build
COPY build.gradle settings.gradle gradle.properties gradlew ./
COPY gradle gradle
COPY src src
RUN ./gradlew shadowJar

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/build/libs/rtmp-*-all.jar app.jar
RUN mkdir -p /app/hls
EXPOSE 8888 1935
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "app.jar"]
