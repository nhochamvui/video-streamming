package com.nhochamvui.rtmp.auth;

public record AuthSession(
        String token,
        String username,
        long expiresAt
) {
    public boolean isExpired(long now) {
        return expiresAt <= now;
    }
}