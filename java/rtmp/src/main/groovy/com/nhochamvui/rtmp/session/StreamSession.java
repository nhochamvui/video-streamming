package com.nhochamvui.rtmp.session;

import java.util.Map;

public record StreamSession(
        String lookupKey,
        String playbackId,
        String assignedServerId,
        StreamSessionStatus status,
        String requestedIp,
        String publisherIp,
        String deviceId,
        String userAgent,
        String connectionId,
        long createdAt,
        long lastValidatedAt,
        long lastHeartbeatAt
) {
    static StreamSession fromRedis(String lookupKey, Map<String, String> values) {
        return new StreamSession(
                lookupKey,
                values.get("playbackId"),
                values.get("assignedServerId"),
                parseStatus(values.get("status")),
                values.get("requestedIp"),
                values.get("publisherIp"),
                values.get("deviceId"),
                values.get("userAgent"),
                values.get("connectionId"),
                parseLong(values.get("createdAt")),
                parseLong(values.get("lastValidatedAt")),
                parseLong(values.get("lastHeartbeatAt"))
        );
    }

    private static StreamSessionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return StreamSessionStatus.PENDING;
        }
        return StreamSessionStatus.valueOf(value);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }
}
