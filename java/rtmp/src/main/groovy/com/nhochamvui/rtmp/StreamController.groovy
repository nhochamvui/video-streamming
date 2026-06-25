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

    @Get("/stats/{streamKey}")
    @Produces(MediaType.APPLICATION_JSON)
    Map streamStats(String streamKey) {
        def session = server.activeStreams[streamKey]
        if (!session) {
            return [error: "Stream not found", streamKey: streamKey]
        }
        return session.statistics
    }

    @Get("/dashboard")
    @Produces(MediaType.TEXT_HTML)
    String dashboard() {
        return """<!DOCTYPE html>
<html>
<head>
    <title>RTMP Dashboard</title>
    <meta charset="utf-8">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { background: #0d1117; color: #c9d1d9; font-family: 'SF Mono', 'Fira Code', monospace; padding: 24px; min-height: 100vh; }
        h1 { color: #58a6ff; font-size: 20px; margin-bottom: 16px; }
        .status-bar { display: flex; gap: 24px; margin-bottom: 20px; padding: 12px 16px; background: #161b22; border-radius: 6px; border: 1px solid #30363d; }
        .status-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
        .status-dot.live { background: #3fb950; box-shadow: 0 0 6px #3fb950; }
        .status-dot.idle { background: #8b949e; }
        .label { color: #8b949e; }
        .value { color: #c9d1d9; font-weight: bold; }
        table { width: 100%; border-collapse: collapse; background: #161b22; border-radius: 6px; border: 1px solid #30363d; overflow: hidden; }
        th { background: #21262d; color: #8b949e; font-weight: normal; text-align: left; padding: 10px 14px; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
        td { padding: 10px 14px; border-top: 1px solid #21262d; font-size: 13px; }
        tr:hover { background: #1c2128; }
        .good { color: #3fb950; }
        .warn { color: #d29922; }
        .bad { color: #f85149; }
        .no-streams { text-align: center; padding: 48px; color: #8b949e; }
        .no-streams .hint { margin-top: 12px; font-size: 12px; color: #484f58; }
        .footer { margin-top: 16px; font-size: 11px; color: #484f58; text-align: right; }
        a { color: #58a6ff; text-decoration: none; }
        a:hover { text-decoration: underline; }
    </style>
</head>
<body>
    <h1>RTMP Dashboard</h1>
    <div class="status-bar">
        <div><span class="status-dot" id="statusDot"></span><span class="label">Status:</span> <span class="value" id="status">--</span></div>
        <div><span class="label">Active streams:</span> <span class="value" id="count">--</span></div>
        <div><span class="label">Updated:</span> <span class="value" id="lastUpdate">--</span></div>
    </div>
    <table><thead><tr>
        <th>Stream</th>
        <th>Uptime</th>
        <th>Audio</th>
        <th>Video</th>
        <th>Bitrate</th>
        <th>Delay</th>
        <th>Keyframes</th>
        <th>Dropped</th>
        <th>FFmpeg</th>
    </tr></thead>
    <tbody id="tbody"><tr><td colspan="9" class="no-streams">Loading...</td></tr></tbody></table>
    <div class="footer">Auto-refresh every 5s | <a href="/">Stream list</a></div>
    <script>
        async function refresh() {
            try {
                const res = await fetch('/stats');
                const data = await res.json();
                const streams = data.activeStreams || {};
                const names = Object.keys(streams);
                document.getElementById('status').textContent = names.length ? 'STREAMING' : 'IDLE';
                document.getElementById('statusDot').className = names.length ? 'status-dot live' : 'status-dot idle';
                document.getElementById('count').textContent = names.length;
                document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString();
                if (!names.length) {
                    document.getElementById('tbody').innerHTML = '<tr><td colspan="9" class="no-streams">No active streams<div class="hint">Connect an RTMP publisher (OBS, ffmpeg) to start</div></td></tr>';
                    return;
                }
                document.getElementById('tbody').innerHTML = names.map(name => {
                    const s = streams[name];
                    const uptime = s.uptimeSec || 0;
                    const delayMs = s.delayMs || 0;
                    const delayClass = delayMs > 5000 ? 'bad' : (delayMs > 2000 ? 'warn' : 'good');
                    const dropped = s.droppedPackets || 0;
                    const droppedClass = dropped > 0 ? 'bad' : 'good';
                    const kf = s.keyframeCount || 0;
                    const gop = s.maxKeyframeIntervalMs ? ((s.maxKeyframeIntervalMs / 1000).toFixed(1) + 's') : '--';
                    let ff = '--';
                    if (s.ffmpegFps || s.ffmpegSpeed) ff = (s.ffmpegFps||'?') + 'fps/' + (s.ffmpegSpeed||'?');
                    return '<tr>' +
                        '<td><a href="/' + name + '">' + name + '</a></td>' +
                        '<td>' + fmtUp(uptime) + '</td>' +
                        '<td>' + (s.audioPackets||0) + 'p / ' + (s.audioBytesHuman||'0B') + '</td>' +
                        '<td>' + (s.videoPackets||0) + 'p / ' + (s.videoBytesHuman||'0B') + '</td>' +
                        '<td>' + (s.bitrateHuman||'--') + '</td>' +
                        '<td class="' + delayClass + '">' + (delayMs/1000).toFixed(1) + 's</td>' +
                        '<td>' + kf + ' (' + gop + ')</td>' +
                        '<td class="' + droppedClass + '">' + dropped + '</td>' +
                        '<td>' + ff + '</td>' +
                    '</tr>';
                }).join('');
            } catch(e) { console.error(e); }
        }
        function fmtUp(s) { const h=Math.floor(s/3600), m=Math.floor((s%3600)/60); return h?h+'h '+m+'m':m?m+'m '+s%60+'s':s+'s'; }
        refresh();
        setInterval(refresh, 5000);
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
