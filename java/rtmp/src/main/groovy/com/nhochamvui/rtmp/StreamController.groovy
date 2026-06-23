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
    <link href="https://vjs.zencdn.net/8.10.0/video-js.css" rel="stylesheet" />
    <style>
        body { margin: 0; background: #111; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        .video-js { width: 100%; max-width: 960px; }
    </style>
</head>
<body>
    <video id="player" class="video-js vjs-default-skin" controls preload="auto" autoplay muted></video>
    <script src="https://vjs.zencdn.net/8.10.0/video.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/videojs-hls-quality-selector@2.0.0/dist/videojs-hls-quality-selector.min.js"></script>
    <script>
        var player = videojs('player', {
            liveui: true,
            liveTracker: {
                trackingThreshold: 0,
                liveTolerance: 5
            },
            html5: {
                vhs: {
                    overrideNative: true,
                    enableLowInitialPlaylist: true,
                    goalBufferLength: 10,
                    maxBufferLength: 15,
                    liveSyncDuration: 3
                },
                nativeAudioTracks: false,
                nativeVideoTracks: false
            },
            sources: [{
                src: '/hls/master.m3u8',
                type: 'application/x-mpegURL'
            }]
        });
        player.hlsQualitySelector();
    </script>
</body>
</html>"""
    }
}
