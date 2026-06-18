package com.nhochamvui.rtmp.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AMF0Decoder {

    public static String getString(ByteBuffer byteBuffer, int length) {
        byte[] data = new byte[length];
        byteBuffer.get(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static Map<String, Object> getMap(ByteBuffer byteBuffer) {
        final Map<String, Object> map = new HashMap<>();
        while (byteBuffer.hasRemaining()) {
            
        }
        return map;
    }
}
