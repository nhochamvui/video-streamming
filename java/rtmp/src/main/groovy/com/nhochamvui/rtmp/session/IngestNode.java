package com.nhochamvui.rtmp.session;

public record IngestNode(
        String serverId,
        String endpoint,
        NodeStatus status,
        int activeStreams,
        double cpuLoad,
        long lastHeartbeatAt
) {
}
