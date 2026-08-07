package com.nhochamvui.rtmp.session;

import java.util.Optional;

public interface StreamSessionRepository {
    void createPending(StreamSession session, int ttlSeconds);

    Optional<StreamSession> validatePublish(String lookupKey, String serverId, String connectionId, String publisherIp, int ttlSeconds);

    boolean heartbeat(String lookupKey, String serverId, String connectionId, int ttlSeconds);

    void disconnect(String lookupKey, String serverId, String connectionId, int ttlSeconds);

    long countByRequestedIpAndStatus(String requestedIp, StreamSessionStatus status);
}
