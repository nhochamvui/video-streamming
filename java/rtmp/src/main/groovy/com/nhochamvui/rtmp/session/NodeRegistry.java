package com.nhochamvui.rtmp.session;

import java.util.Optional;

public interface NodeRegistry {
    Optional<IngestNode> selectLeastLoadedNode();

    void heartbeat(int activeStreams);
}
