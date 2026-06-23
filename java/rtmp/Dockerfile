FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY build/libs/rtmp-0.1-all.jar app.jar
RUN mkdir -p /app/hls
EXPOSE 8888 1935
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "app.jar"]
