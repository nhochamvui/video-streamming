package com.nhochamvui.rtmp.session;

public class StreamSessionLimitExceeded extends RuntimeException {
    public StreamSessionLimitExceeded(String message) {
        super(message);
    }
}
