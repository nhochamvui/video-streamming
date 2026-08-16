package com.nhochamvui.rtmp.session;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.regex.Pattern;

@Singleton
public class SafePlaybackPath {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path hlsRoot;

    public SafePlaybackPath(@Value("${rtmp.hls.root:hls}") String hlsRoot) {
        this.hlsRoot = Path.of(hlsRoot).toAbsolutePath().normalize();
    }

    public Path streamDirectory(String playbackId) {
        if (playbackId == null || !SAFE_ID.matcher(playbackId).matches()) {
            throw new IllegalArgumentException("Invalid playbackId");
        }
        Path resolved = hlsRoot.resolve(playbackId).normalize();
        if (!resolved.startsWith(hlsRoot)) {
            throw new IllegalArgumentException("playbackId escapes HLS root");
        }
        return resolved;
    }
}
