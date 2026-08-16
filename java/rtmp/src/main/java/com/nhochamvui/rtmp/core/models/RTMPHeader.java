package com.nhochamvui.rtmp.core.models;

public class RTMPHeader {

    /**
     * Basic header (1, 2, 3 Bytes)
     */
    public Basic basic;

    /**
     * Message header (0, 3, 7, 11 Bytes)
     */
    public Message message;

    /**
     * Extended timestamp (0, 4 Bytes)
     */
    public int extendedTimestamp;

    public RTMPHeader() {
        this.basic = new Basic();
        this.message = new Message();
    }
}

