package com.nhochamvui.rtmp.session;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Singleton
public class StreamKeyGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generatePublishKey() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String generatePlaybackId() {
        return UUID.randomUUID().toString();
    }
}
