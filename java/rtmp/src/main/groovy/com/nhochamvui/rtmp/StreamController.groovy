package com.nhochamvui.rtmp

import com.nhochamvui.rtmp.core.Server
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces

@Controller("/")
class StreamController {

    private final Server server
    private String indexHtml

    @io.micronaut.context.annotation.Value('${rtmp.hls.cdn-url:}')
    String hlsCdnUrl

    StreamController(Server server) {
        this.server = server
    }

    @Get("/config")
    @Produces(MediaType.APPLICATION_JSON)
    Map config() {
        return [
            hlsCdn: hlsCdnUrl
        ]
    }

    @Get(produces = MediaType.TEXT_HTML)
    String index() {
        return renderIndex()
    }

    @Get("/dashboard")
    @Produces(MediaType.TEXT_HTML)
    String dashboard() {
        return renderIndex()
    }

    @Get("/{playbackId}")
    @Produces(MediaType.TEXT_HTML)
    String streamPlayer(String playbackId) {
        return renderIndex()
    }

    @Get("/health")
    @Produces(MediaType.APPLICATION_JSON)
    Map health() {
        def names = server.activeStreamNames
        return [
            status    : names.isEmpty() ? "idle" : "streaming",
            activeStreams: names.size(),
            streams   : names as List
        ]
    }

    @Get("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    Map stats() {
        def streams = server.activeStreams
        return [
            activeStreams: streams.collectEntries { name, session -> [(name): session.statistics] }
        ]
    }

    @Get("/stats/{playbackId}")
    @Produces(MediaType.APPLICATION_JSON)
    Map streamStats(String playbackId) {
        def session = server.activeStreams[playbackId]
        if (!session) {
            return [error: "Stream not found", playbackId: playbackId]
        }
        return session.statistics
    }

    @Get("/version")
    @Produces(MediaType.TEXT_PLAIN)
    String version() {
        String v = getClass().getPackage().getImplementationVersion()
        return v != null ? v : "unknown"
    }

    private String renderIndex() {
        if (indexHtml == null) {
            def resource = getClass().getResourceAsStream("/static/index.html")
            if (resource == null) {
                indexHtml = """<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>RTMP Server</title>
    <style>
        body { font-family: monospace; padding: 2em; background: #101418; color: #e5edf3; }
        a { color: #79c0ff; }
    </style>
</head>
<body>
    <h1>RTMP Server</h1>
    <p>Frontend assets not found. Build the React app in <code>frontend/</code> and copy <code>dist/</code> into <code>src/main/resources/static</code>.</p>
</body>
</html>"""
            } else {
                indexHtml = resource.getText("UTF-8")
                resource.close()
            }
        }
        return indexHtml
    }
}