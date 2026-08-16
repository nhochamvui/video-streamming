package com.nhochamvui.rtmp.session;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Singleton
public class StreamKeyHasher {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] serverSecret;

    public StreamKeyHasher(@Value("${rtmp.auth.hmac-secret:123456789123456789123456789123456789}") String serverSecret) {
        this.serverSecret = serverSecret == null ? new byte[0] : serverSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String lookupKey(String publishKey) {
        if (serverSecret.length < 32) {
            throw new IllegalStateException("rtmp.auth.hmac-secret must be configured with at least 32 bytes");
        }
        return hmacHex(publishKey);
    }

    public String fingerprint(String publishKey) {
        if (serverSecret.length < 32) {
            return "unconfigured";
        }
        return hmacHex("fingerprint:" + publishKey).substring(0, 12);
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(serverSecret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash stream key", e);
        }
    }
}
