package com.nhochamvui.rtmp.core.enums;

public enum AMF0Type {
    NUMBER(0x00), BOOLEAN(0x01), STRING(0x02), OBJECT(0x03), NULL(0x05), UNDEFINED(0x06), MAP(0x08), ARRAY(0x0A),
    DATE(0x0B), UNSUPPORTED(0x0D);

    public final int value;

    private AMF0Type(int value) {
        this.value = value;
    }
}
