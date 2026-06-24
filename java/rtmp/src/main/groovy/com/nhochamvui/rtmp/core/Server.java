package com.nhochamvui.rtmp.core;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class Server {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Server.class);

    private final ConcurrentHashMap<String, ClientSession> streams = new ConcurrentHashMap<>();

    public Server() {
        log.info("RTMP Server initialized");
    }

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(1935, 50, InetAddress.getByName("0.0.0.0"))) {
            serverSocket.setReuseAddress(true);
            log.info("RTMP server is listening on port 1935...");
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread.ofVirtual().start(() -> {
                        try (socket) {
                            new ClientSession(socket, Server.this).run();
                        } catch (Exception e) {
                            log.error("ClientSession fatal error", e);
                        }
                    });
                } catch (IOException e) {
                    log.error("Failed to accept connection: {}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("Server socket error: {}", e.getMessage(), e);
        }
    }

    void registerStream(String name, ClientSession session) {
        ClientSession old = streams.put(name, session);
        if (old != null) {
            log.info("Kicking previous streamer for key: {}", name);
            old.close();
        }
    }

    void unregisterStream(String name, ClientSession session) {
        streams.remove(name, session);
    }

    public Set<String> getActiveStreamNames() {
        return Set.copyOf(streams.keySet());
    }

    public boolean hasStream(String name) {
        return streams.containsKey(name);
    }

    public Map<String, ClientSession> getActiveStreams() {
        return Map.copyOf(streams);
    }
}
