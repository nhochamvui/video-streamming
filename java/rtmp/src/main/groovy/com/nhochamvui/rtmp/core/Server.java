package com.nhochamvui.rtmp.core;

import com.github.javaparser.utils.Log;
import com.nhochamvui.rtmp.core.enums.ReadState;
import com.nhochamvui.rtmp.core.models.Basic;
import com.nhochamvui.rtmp.core.models.Message;
import com.nhochamvui.rtmp.core.models.RTMPHeader;
import groovy.util.logging.Slf4j;
import jakarta.inject.Singleton;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static com.nhochamvui.rtmp.core.constants.Constant.MASK_OF_6_BITS;
import static com.nhochamvui.rtmp.core.constants.Constant.MASK_OF_8_BITS;

@Singleton
@Slf4j
public class Server {

    public static final int HANDSHAKE_LENGTH = 1536;
    public static final int VERSION_LENGTH = 1;
    public static final int DEFAULT_VERSION = 3;

    private static final Random randomSeed = new Random();

    private ReadState readState;
    private int clientMessageChunkSize = 128;

    /**
     * Contains previous headers grouped by chunk stream ID
     */
    private final Map<Integer, RTMPHeader> prevHeaders = new HashMap<>();

    /**
     * Payload grouped by chunk stream ID
     */
    private final Map<Integer, ByteBuffer> chunkPayload = new HashMap<>();

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(1935, 50, InetAddress.getByName("127.0.0.1"))) {
            serverSocket.setReuseAddress(true);
            System.out.println("RTMP server is listening on port 1935...");
            Socket socket = serverSocket.accept();

            handleHandShake(socket.getInputStream(), socket.getOutputStream());
            while (true) {
                try {
                    handleChunkMessage(socket.getInputStream(), socket.getOutputStream());

                } catch (Exception ex) {
                    break;
                }
            }

            socket.close();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    /**
     * Handle reading a chunk message
     *
     * @param inputStream
     * @param outputStream
     * @throws IOException
     */
    private void handleChunkMessage(InputStream inputStream, OutputStream outputStream) throws IOException {
        final RTMPHeader header = readRTMPHeader(inputStream);
        prevHeaders.put(header.basic.csid, header);
        chunkPayload.putIfAbsent(
                header.basic.csid, ByteBuffer.allocateDirect(header.message.length)
        );
        ByteBuffer currentMessageData = chunkPayload.get(header.basic.csid);
        assert currentMessageData != null;
        if (currentMessageData.hasRemaining()) {
            int maxLengthForCurrentChunkData = Math.min(header.message.length, clientMessageChunkSize);
            currentMessageData.put(inputStream.readNBytes(maxLengthForCurrentChunkData));
            if (!currentMessageData.hasRemaining()) {
                System.out.println("Finished chunk: " + header.basic.csid + ", message stream: " + header.message.streamId);
                switch (header.message.typeId) {
                    case 1:
                        this.clientMessageChunkSize = currentMessageData.getInt(0);
                        System.out.println("Process SetChunkSize message, new chunk size: " + this.clientMessageChunkSize);
                        break;
                    case 2:
                        System.out.println("Process AbortMessage message");
                        break;
                    case 3:
                        System.out.println("Process Acknowledgement message");
                        break;
                    case 4:
                        System.out.println("Process UserControl message");
                        break;
                    case 5:
                        System.out.println("Process Window Acknowledgement Size message");
                        break;
                    case 6:
                        System.out.println("Process Set Peer Bandwidth message");
                        break;
                    case 8:
                        System.out.println("Process Audio message");
                        break;
                    case 9:
                        System.out.println("Process Video message");
                        break;
                    case 15:
                        System.out.println("Process AMF3 Command message");
                        break;
                    case 20:
                        System.out.println("Process AMF0 Command message");
                        break;
                }
                chunkPayload.remove(header.basic.csid);
            }
        }
    }

    /**
     * Read and parse RTMP header (with basic header and message header) from input stream
     *
     * @param inputStream
     * @return The RTMP header
     * @throws IOException
     */
    private RTMPHeader readRTMPHeader(InputStream inputStream) throws IOException {
        final RTMPHeader rtmpHeader = new RTMPHeader();
        final Basic basicHeader = rtmpHeader.basic;
        byte first = inputStream.readNBytes(1)[0];

        // fmt is the first 2 bits.
        basicHeader.fmt = (first & MASK_OF_8_BITS) >> 6;

        // mask the first 2 bits and keeps the rest.
        int csid = first & MASK_OF_6_BITS;

        // 2 bytes header form
        if (csid == 0) {
            csid = inputStream.readNBytes(1)[0] & MASK_OF_8_BITS + 64;
        } else if (csid == 1) { // 3 bytes header form
            byte[] bytes = inputStream.readNBytes(2);
            csid = (bytes[0] & MASK_OF_8_BITS + 64) + (bytes[1] & MASK_OF_8_BITS) << 8;
        }
        basicHeader.csid = csid;
        System.out.println("Reading chunk " + basicHeader.csid);

        final Message message = rtmpHeader.message;
        switch (basicHeader.fmt) {
            case 0: //11 bytes
                message.timestamp = readIntFrom3Bytes(inputStream);
                message.length = readIntFrom3Bytes(inputStream);
                message.typeId = inputStream.read();
                message.streamId = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();
                break;
            case 1: // 7 bytes
                message.timestampDelta = readIntFrom3Bytes(inputStream);
                message.length = readIntFrom3Bytes(inputStream);
                message.typeId = inputStream.read();
                message.streamId = prevHeaders.get(basicHeader.csid).message.streamId;
                break;
            case 2: // 3 bytes
                message.timestampDelta = readIntFrom3Bytes(inputStream);
                message.length = prevHeaders.get(basicHeader.csid).message.length;
                message.typeId = prevHeaders.get(basicHeader.csid).message.typeId;
                message.streamId = prevHeaders.get(basicHeader.csid).message.streamId;
                break;
            case 3: // 0 bytes
                message.timestampDelta = prevHeaders.get(basicHeader.csid).message.timestampDelta;
                message.timestamp = prevHeaders.get(basicHeader.csid).message.timestamp;
                message.length = prevHeaders.get(basicHeader.csid).message.length;
                message.typeId = prevHeaders.get(basicHeader.csid).message.typeId;
                message.streamId = prevHeaders.get(basicHeader.csid).message.streamId;
                break;
        }

        if (message.timestamp >= 255) {
            rtmpHeader.extendedTimestamp = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();
        }

        return rtmpHeader;
    }

    public static void handleHandShake(InputStream reader, OutputStream out) throws IOException {
        System.out.println("Handshake started.");

        // C0 is a byte indicates the requested version from RTMP client
        int c0 = reader.read();
        byte[] c1 = readToBuffer(reader, HANDSHAKE_LENGTH);
        sendS0S1(out, c0);
        sendS2(out, c1);
        byte[] c2 = readToBuffer(reader, HANDSHAKE_LENGTH);

        System.out.println("Handshake finished.");
    }

    private static void sendS2(OutputStream out, byte[] c1) throws IOException {
        byte[] c1Time = Arrays.copyOfRange(c1, 0, 3);
        byte[] c1RandBytes = Arrays.copyOfRange(c1, 8, c1.length);
        byte[] s2 = new byte[HANDSHAKE_LENGTH];
        System.arraycopy(c1Time, 0, s2, 0, c1Time.length);
        //TODO: should fill C1 reception timestamp from 4 to 7 index
        System.arraycopy(c1RandBytes, 0, s2, 8, c1RandBytes.length);
        out.write(s2);
    }

    public static int determineVersion(int requestedVersion) {
        switch (requestedVersion) {
            case 0, 2:
                System.out.println("Reject deprecated version.");
            default:
                System.out.println("Selected version: " + DEFAULT_VERSION);
                return DEFAULT_VERSION;
        }
    }

    private static void sendS0S1(OutputStream out, int c0) throws IOException {
        // S0 - version
        int s0 = determineVersion(c0);

        // the first 4 bytes are timestamp (or 0) and next 4 bytes must be 0. The last bytes are random bytes
        byte[] s1 = new byte[HANDSHAKE_LENGTH + 8];
        System.arraycopy(generateRandomBytes(HANDSHAKE_LENGTH), 0, s1, 8, HANDSHAKE_LENGTH);

        out.write(s0);
        out.write(s1);
    }

    public static byte[] readToBuffer(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        if (in.read(buffer) == -1) {
            throw new IOException("End of stream");
        }
        return buffer;
    }

    public static void fillRandomBytes(byte[] arr, int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            arr[i] = (byte) randomSeed.nextInt(256);
        }
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        randomSeed.nextBytes(bytes);
        return bytes;
    }

    @Deprecated
    public static byte[] slowReadToBuffer(BufferedReader in, int length) throws IOException {
        int readByte;
        byte[] buffer = new byte[length];
        int i = 0;
        final long then = System.currentTimeMillis();
        while ((readByte = in.read()) != -1) {
            buffer[i] = (byte) readByte;
            i++;
        }
        final long now = System.currentTimeMillis();
        System.out.println("Finish in " + ((now - then) / 1000));
        return buffer;
    }

    private int readIntFrom3Bytes(InputStream inputStream) throws IOException {
        byte[] threeBytes = inputStream.readNBytes(3);
        byte[] fourBytes = new byte[4];
        System.arraycopy(threeBytes, 0, fourBytes, 1, threeBytes.length);
        return ByteBuffer.wrap(fourBytes).getInt();
    }
}

