package com.nhochamvui.rtmp.auth;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AuthSessionStore {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final AuthProperties properties;
    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    public AuthSessionStore(AuthProperties properties) {
        this.properties = properties;
    }

    public String create(String username) {
        String token = HexFormat.of().formatHex(randomBytes());
        sessions.put(token, new AuthSession(token, username, System.currentTimeMillis() + properties.getSessionTtlSeconds() * 1000L));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        AuthSession session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.isExpired(System.currentTimeMillis())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public Optional<String> username(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = sessions.get(token);
        if (session == null || session.isExpired(System.currentTimeMillis())) {
            return Optional.empty();
        }
        return Optional.of(session.username());
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public int size() {
        return sessions.size();
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return bytes;
    }
}