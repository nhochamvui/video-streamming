package com.nhochamvui.rtmp.session;

public record StreamSessionCreateRequest(
        String requestedIp,
        String userAgent,
        String deviceId
) {
}
