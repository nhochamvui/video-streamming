package com.nhochamvui.rtmp.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

@Controller("/api/v1/auth")
public class AuthController {
    private final AuthProperties properties;
    private final AuthSessionStore sessionStore;

    public AuthController(AuthProperties properties, AuthSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
    }

    @Post("/login")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<?> login(@Body LoginRequest request) {
        if (request == null || !validCredentials(request.username(), request.password())) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
        String token = sessionStore.create(request.username());
        Cookie cookie = Cookie.of(properties.getCookieName(), token)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(SameSite.Lax)
                .path("/")
                .maxAge(properties.getSessionTtlSeconds());
        return HttpResponse.ok(Map.of("message", "Login successful", "username", request.username())).cookie(cookie);
    }

    @Post("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<?> logout(HttpRequest<?> request) {
        Optional<Cookie> cookie = request.getCookies().findCookie(properties.getCookieName());
        cookie.ifPresent(c -> sessionStore.invalidate(c.getValue()));
        Cookie cleared = Cookie.of(properties.getCookieName(), "").maxAge(0);
        return HttpResponse.ok(Map.of("message", "Logged out")).cookie(cleared);
    }

    @Get("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> me(HttpRequest<?> request) {
        Optional<Cookie> cookie = request.getCookies().findCookie(properties.getCookieName());
        Optional<String> username = cookie.flatMap(c -> sessionStore.username(c.getValue()));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("authenticated", username.isPresent());
        result.put("username", username.orElse(null));
        return result;
    }

    private boolean validCredentials(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return constantTimeEquals(username, properties.getUsername()) && constantTimeEquals(password, properties.getPassword());
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}