package com.nhochamvui.rtmp.core.models;

import groovy.lang.Tuple2;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class AMF0Message extends LinkedHashMap<String, Object> {
    public static final byte[] OBJECT_END_MARKER = new byte[]{0x00, 0x00, 0x09};

    public AMF0Message(List<Tuple2<String, Object>> values) {
        values.forEach((tuple2) -> {
            this.put(tuple2.getV1(), tuple2.getV2());
        });
    }

    public static enum Type {
        NUMBER,
        BOOLEAN,
        STRING,
        OBJECT,
    }
}
