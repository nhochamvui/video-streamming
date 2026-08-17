package com.nhochamvui.rtmp.auth;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ConfigurationProperties("rtmp.auth")
public class AuthProperties {
    private static final Logger LOG = LoggerFactory.getLogger(AuthProperties.class);

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;
    private String cookieName = "rtmp_session";
    private int sessionTtlSeconds = 86400;
    private boolean cookieSecure = false;

    @PostConstruct
    void warnAboutDefaultCredentials() {
        if (DEFAULT_USERNAME.equals(username) || DEFAULT_PASSWORD.equals(password)) {
            LOG.warn("rtmp.auth username/password are using default values. Set RTMP_AUTH_USERNAME and RTMP_AUTH_PASSWORD in production.");
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public int getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(int sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }
}