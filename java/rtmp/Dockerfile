FROM golang:1.25-alpine AS go-builder
WORKDIR /build/go-hls
COPY go-hls/go.mod go-hls/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY go-hls/ ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/hls-segmenter .

FROM gradle:8-jdk21 AS builder
WORKDIR /build
COPY build.gradle settings.gradle gradle.properties gradlew ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre
RUN mkdir -p /app/hls
WORKDIR /app
COPY --from=builder /build/build/libs/rtmp-*-all.jar app.jar
COPY --from=go-builder /out/hls-segmenter /usr/local/bin/hls-segmenter
RUN chmod +x /usr/local/bin/hls-segmenter
EXPOSE 8888 1935
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "app.jar"]