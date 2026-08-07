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
        def links = streamNames.collect { name ->
            String safeName = html(name)
            return """<li><a href="/${safeName}">${safeName}</a></li>"""
        }.join("\n")
        def streamList = streamNames.isEmpty()
                ? """<p class="empty">No active streams.</p>"""
                : """<ul class="stream-list">${links}</ul>"""
        return """<!DOCTYPE html>
<html>
<head>
    <title>RTMP Server</title>
    <meta charset="utf-8">
    <link rel="icon" type="image/svg+xml" href="/static/favicon.svg">
    <style>
        * { box-sizing: border-box; }
        body {
            margin: 0;
            min-height: 100vh;
            background: #101418;
            color: #e5edf3;
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            padding: 32px;
        }
        main { max-width: 960px; margin: 0 auto; }
        h1 { font-size: 22px; margin: 0 0 8px; }
        h2 { font-size: 15px; margin: 0 0 14px; color: #aab7c4; }
        p { margin: 0; }
        .section {
            border: 1px solid #2d3741;
            background: #171d23;
            border-radius: 8px;
            padding: 18px;
            margin-top: 20px;
        }
        .intro { color: #aab7c4; margin-bottom: 16px; line-height: 1.5; }
        button, .button-link {
            appearance: none;
            border: 1px solid #3d8bfd;
            background: #1f6feb;
            color: white;
            border-radius: 6px;
            padding: 8px 12px;
            font: inherit;
            cursor: pointer;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 36px;
        }
        button.secondary, .button-link.secondary {
            background: #222a32;
            border-color: #3a4652;
            color: #d7e0e8;
        }
        button:disabled, .button-link.disabled {
            cursor: not-allowed;
            opacity: 0.55;
        }
        .result {
            display: none;
            margin-top: 16px;
            gap: 12px;
        }
        .result.visible { display: grid; }
        .field {
            display: grid;
            grid-template-columns: 140px minmax(0, 1fr) auto;
            gap: 10px;
            align-items: center;
            padding: 10px;
            border: 1px solid #2d3741;
            border-radius: 6px;
            background: #101418;
        }
        .label { color: #8f9daa; font-size: 12px; text-transform: uppercase; }
        code {
            min-width: 0;
            overflow-wrap: anywhere;
            color: #f0f6fc;
            font-size: 13px;
        }
        .status { margin-top: 12px; min-height: 20px; color: #aab7c4; }
        .status.error { color: #ff8d8d; }
        .status.ok { color: #7ee787; }
        .empty { color: #8f9daa; }
        .stream-list { margin: 0; padding-left: 22px; }
        a { color: #79c0ff; }
        @media (max-width: 720px) {
            body { padding: 18px; }
            .field { grid-template-columns: 1fr; }
            button, .button-link { width: 100%; }
        }
    </style>
</head>
<body>
<main>
    <h1>RTMP Server</h1>
    <p class="intro">Create a temporary stream session, then paste the server URL and stream key into OBS.</p>

    <section class="section">
        <h2>Streamer Setup</h2>
        <button id="createSession" type="button">Create stream session</button>
        <div id="status" class="status"></div>
        <div id="sessionResult" class="result" aria-live="polite">
            <div class="field">
                <span class="label">Server URL</span>
                <code id="serverUrl"></code>
                <button class="secondary" type="button" data-copy="serverUrl">Copy</button>
            </div>
            <div class="field">
                <span class="label">Stream Key</span>
                <code id="streamKey"></code>
                <button class="secondary" type="button" data-copy="streamKey">Copy</button>
            </div>
            <div class="field">
                <span class="label">Playback URL</span>
                <code id="playbackUrl"></code>
                <a id="openPlayback" class="button-link secondary" href="#" target="_blank" rel="noopener">Open</a>
            </div>
            <div class="field">
                <span class="label">Expires In</span>
                <code id="expiresIn">--</code>
                <button id="newSession" class="secondary" type="button">Generate new</button>
            </div>
        </div>
    </section>

    <section class="section">
        <h2>Active Streams</h2>
        ${streamList}
    </section>
</main>
<script>
    let expiresAt = 0;
    let countdownTimer = null;
    let currentSession = null;

    const createButton = document.getElementById('createSession');
    const newButton = document.getElementById('newSession');
    const statusEl = document.getElementById('status');
    const resultEl = document.getElementById('sessionResult');

    createButton.addEventListener('click', createStreamSession);
    newButton.addEventListener('click', createStreamSession);
    document.querySelectorAll('[data-copy]').forEach(button => {
        button.addEventListener('click', () => copyValue(button.dataset.copy, button));
    });

    async function createStreamSession() {
        setStatus('Creating stream session...', '');
        setBusy(true);
        try {
            const response = await fetch('/api/v1/stream-sessions', { method: 'POST' });
            const body = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(body.error || 'Could not create stream session');
            }
            currentSession = body;
            document.getElementById('serverUrl').textContent = body.serverUrl || '';
            document.getElementById('streamKey').textContent = body.streamKey || '';
            document.getElementById('playbackUrl').textContent = body.playbackUrl || '';
            document.getElementById('openPlayback').href = body.playbackUrl || '#';
            resultEl.classList.add('visible');
            expiresAt = Date.now() + ((body.expiresInSeconds || 0) * 1000);
            startCountdown();
            setStatus('Stream key created. Start publishing before it expires.', 'ok');
        } catch (error) {
            setStatus(error.message, 'error');
        } finally {
            setBusy(false);
        }
    }

    function startCountdown() {
        clearInterval(countdownTimer);
        updateCountdown();
        countdownTimer = setInterval(updateCountdown, 1000);
    }

    function updateCountdown() {
        const remaining = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
        const minutes = String(Math.floor(remaining / 60)).padStart(2, '0');
        const seconds = String(remaining % 60).padStart(2, '0');
        document.getElementById('expiresIn').textContent = minutes + ':' + seconds;
        if (remaining === 0 && currentSession) {
            clearInterval(countdownTimer);
            currentSession = null;
            document.querySelectorAll('[data-copy]').forEach(button => button.disabled = true);
            document.getElementById('openPlayback').classList.add('disabled');
            document.getElementById('openPlayback').removeAttribute('href');
            setStatus('This stream key has expired. Generate a new session before publishing.', 'error');
        } else {
            document.querySelectorAll('[data-copy]').forEach(button => button.disabled = false);
            document.getElementById('openPlayback').classList.remove('disabled');
        }
    }

    async function copyValue(id, button) {
        const value = document.getElementById(id).textContent;
        if (!value || !currentSession) return;
        await navigator.clipboard.writeText(value);
        const original = button.textContent;
        button.textContent = 'Copied';
        setTimeout(() => button.textContent = original, 1200);
    }

    function setBusy(busy) {
        createButton.disabled = busy;
        newButton.disabled = busy;
    }

    function setStatus(message, type) {
        statusEl.textContent = message;
        statusEl.className = 'status' + (type ? ' ' + type : '');
    }
</script>
</body>
</html>"""
    }

    @Get("/{playbackId}")
    @Produces(MediaType.TEXT_HTML)
    String streamPlayer(String playbackId) {
        String safePlaybackId = html(playbackId)
        if (!server.hasStream(playbackId)) {
            return """<!DOCTYPE html>
        <html>
        <head><title>Stream Not Found</title></head>
        <body style="font-family: monospace; padding: 2em;">
            <h1>Stream not found: ${safePlaybackId}</h1>
            <p><a href="/">Back to stream list</a></p>
        </body>
        </html>"""
                }
                return """<!DOCTYPE html>
        <html>
        <head>
            <title>Live Stream - ${safePlaybackId}</title>
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
                        src: '/hls/${safePlaybackId}/master.m3u8',
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

    @Get("/stats/{playbackId}")
    @Produces(MediaType.APPLICATION_JSON)
    Map streamStats(String playbackId) {
        def session = server.activeStreams[playbackId]
        if (!session) {
            return [error: "Stream not found", playbackId: playbackId]
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
                        '<td><a href="/' + encodeURIComponent(name) + '">' + esc(name) + '</a></td>' +
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
        function esc(s) {
            return String(s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
        }
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

    private static String html(Object value) {
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}
