package com.nhochamvui.rtmp.session;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Singleton
public class RedisProvider {
    private final RedisClient client;
    private StatefulRedisConnection<String, String> connection;

    public RedisProvider(@Value("${redis.uri}") String redisUri) {
        this.client = RedisClient.create(redisUri);
    }

    public synchronized RedisCommands<String, String> commands() {
        if (connection == null || !connection.isOpen()) {
            connection = client.connect();
        }
        return connection.sync();
    }

    @PreDestroy
    void close() {
        if (connection != null) {
            connection.close();
        }
        client.shutdown();
    }
}
