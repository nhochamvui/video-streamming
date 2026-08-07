package com.nhochamvui.rtmp.session;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CreateStreamSessionResponse(
        String serverUrl,
        String streamKey,
        String playbackUrl,
        int expiresInSeconds
) {
}
