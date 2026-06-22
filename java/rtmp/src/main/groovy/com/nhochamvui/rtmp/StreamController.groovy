package com.nhochamvui.rtmp

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/")
class StreamController {

    @Get(produces = MediaType.TEXT_HTML)
    String index() {
        return """<!DOCTYPE html>
<html>
<head>
    <title>Live Stream</title>
    <style>
        body { margin: 0; display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #111; }
        video { width: 100%; max-width: 960px; }
    </style>
</head>
<body>
    <video id="v" controls autoplay muted crossOrigin="anonymous"></video>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <script>
        var video = document.getElementById('v');
        if (Hls.isSupported()) {
            var hls = new Hls({
                liveSyncDurationCount: 3,
                maxBufferLength: 10,
                maxMaxBufferLength: 15
            });
            hls.loadSource('/hls/output.m3u8');
            hls.attachMedia(video);
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = '/hls/output.m3u8';
        }
    </script>
</body>
</html>"""
    }
}
