package com.nhochamvui.rtmp

import com.nhochamvui.rtmp.core.Server
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces

@Controller("/")
class StreamController {

    private final Server server

    StreamController(Server server) {
        this.server = server
    }

    @Get(produces = MediaType.TEXT_HTML)
    String index() {
        def streamNames = server.activeStreamNames
        if (streamNames.isEmpty()) {
            return """<!DOCTYPE html>
<html>
<head><title>RTMP Server</title></head>
<body style="font-family: monospace; padding: 2em;">
    <h1>RTMP Server</h1>
    <p>No active streams.</p>
    <p>Connect an RTMP publisher (OBS, ffmpeg) to start streaming.</p>
    <p>Stream URL: <code>rtmp://host:1935/live/{stream-key}</code></p>
</body>
</html>"""
        }
        def links = streamNames.collect { name ->
            """<li><a href="/${name}">${name}</a></li>"""
        }.join("\n")
        return """<!DOCTYPE html>
<html>
<head><title>RTMP Streams</title></head>
<body style="font-family: monospace; padding: 2em;">
    <h1>Active Streams</h1>
    <ul>${links}</ul>
</body>
</html>"""
    }

    @Get("/{streamKey}")
    @Produces(MediaType.TEXT_HTML)
    String streamPlayer(String streamKey) {
        if (!server.hasStream(streamKey)) {
            return """<!DOCTYPE html>
<html>
<head><title>Stream Not Found</title></head>
<body style="font-family: monospace; padding: 2em;">
    <h1>Stream not found: ${streamKey}</h1>
    <p><a href="/">Back to stream list</a></p>
</body>
</html>"""
        }
        return """<!DOCTYPE html>
<html>
<head>
    <title>Live Stream - ${streamKey}</title>
    <link rel="icon" type="image/svg+xml" href="/static/favicon.svg">
    <link href="https://vjs.zencdn.net/8.10.0/video-js.css" rel="stylesheet" />
    <style>
        body { margin: 0; background: #111; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
        .video-js { width: 100%; max-width: 960px; }
    </style>
</head>
<body>
    <video id="player" class="video-js vjs-default-skin" controls preload="auto" autoplay muted></video>
    <script src="https://vjs.zencdn.net/8.10.0/video.min.js"></script>
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
                src: '/hls/${streamKey}/master.m3u8',
                type: 'application/x-mpegURL'
            }]
        });
    </script>
</body>
</html>"""
    }

    @Get("/version")
    @Produces(MediaType.TEXT_PLAIN)
    String version() {
        String v = getClass().getPackage().getImplementationVersion()
        return v != null ? v : "unknown"
    }
}
