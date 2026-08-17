package com.nhochamvui.rtmp.auth;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record LoginRequest(
        String username,
        String password
) {
}