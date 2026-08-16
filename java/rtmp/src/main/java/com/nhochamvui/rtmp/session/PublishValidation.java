package com.nhochamvui.rtmp.session;

public record PublishValidation(
        String lookupKey,
        String playbackId,
        String assignedServerId,
        String keyFingerprint
) {
}
