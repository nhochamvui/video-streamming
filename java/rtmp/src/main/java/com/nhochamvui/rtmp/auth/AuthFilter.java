package com.nhochamvui.rtmp.auth;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.cookie.Cookie;

import java.util.Map;
import java.util.Optional;

@ServerFilter("/api/v1/stream-sessions/**")
public class AuthFilter {
    private final AuthProperties properties;
    private final AuthSessionStore sessionStore;

    public AuthFilter(AuthProperties properties, AuthSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
    }

    @RequestFilter
    @Nullable
    public MutableHttpResponse<?> filterRequest(HttpRequest<?> request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return null;
        }
        Optional<Cookie> cookie = request.getCookies().findCookie(properties.getCookieName());
        boolean authenticated = cookie.map(c -> sessionStore.isValid(c.getValue())).orElse(false);
        if (authenticated) {
            return null;
        }
        return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthorized"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    }
}