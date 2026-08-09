package com.nhochamvui.rtmp.session;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.util.UUID;

@Singleton
public class ServerIdentity {
    private final String serverId;

    public ServerIdentity(@Value("${rtmp.server-id:local-node}") String configuredServerId) {
        this.serverId = resolve(configuredServerId);
    }

    public String serverId() {
        return serverId;
    }

    private static String resolve(String configuredServerId) {
        if (configuredServerId != null && !configuredServerId.isBlank() && !"auto".equalsIgnoreCase(configuredServerId)) {
            return configuredServerId;
        }
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                hostname = "node-" + UUID.randomUUID();
            }
        }
        return "cheap-" + hostname.replaceAll("[^A-Za-z0-9_.-]", "-");
    }
}
