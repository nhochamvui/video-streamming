package com.nhochamvui.rtmp.core.functions;

import java.io.IOException;
import java.io.OutputStream;

public class MediaHandler {
    public static void writeFlvTag(OutputStream outputStream,
                                   byte tagType, int timestamp, byte[] payload) throws IOException {
        int dataSize = payload.length;
        byte[] tagHeader = new byte[11];

        // 1. Tag Type (1 byte): 8=audio, 9=video, 18=script
        tagHeader[0] = tagType;

        // 2. Data Size
        tagHeader[1] = (byte) ((dataSize >> 16) & 0xFF);
        tagHeader[2] = (byte) ((dataSize >> 8) & 0xFF);
        tagHeader[3] = (byte) (dataSize & 0xFF);

        // 3. Timestamp
        tagHeader[4] = (byte) ((timestamp >> 16) & 0xFF);
        tagHeader[5] = (byte) ((timestamp >> 8) & 0xFF);
        tagHeader[6] = (byte) (timestamp & 0xFF);
        tagHeader[7] = (byte) ((timestamp >> 24) & 0xFF);

        // Stream ID
        tagHeader[8] = 0;
        tagHeader[9] = 0;
        tagHeader[10] = 0;

        outputStream.write(tagHeader);

        outputStream.write(payload);

        int previousTagSize = 11 + dataSize;
        byte[] pvsBuf = new byte[4];
        pvsBuf[0] = (byte) ((previousTagSize >> 24) & 0xFF);
        pvsBuf[1] = (byte) ((previousTagSize >> 16) & 0xFF);
        pvsBuf[2] = (byte) ((previousTagSize >> 8) & 0xFF);
        pvsBuf[3] = (byte) (previousTagSize & 0xFF);

        outputStream.write(pvsBuf);
        outputStream.flush();
    }

    public static byte[] createFlvHeader() {
        return new byte[] {
                0x46, 0x4c, 0x56,        // 'F', 'L', 'V'
                0x01,                    // Version 1
                0x05,                    // TypeFlags: Audio + Video
                0x00, 0x00, 0x00, 0x09,  // Header Size: 9
                0x00, 0x00, 0x00, 0x00   // Previous Tag Size 0
        };
    }
}
