package com.nhochamvui.rtmp.core;

import com.nhochamvui.rtmp.core.enums.ReadState;
import com.nhochamvui.rtmp.core.models.AMF0Message;
import com.nhochamvui.rtmp.core.models.Basic;
import com.nhochamvui.rtmp.core.models.Message;
import com.nhochamvui.rtmp.core.models.RTMPHeader;
import groovy.lang.Tuple2;
import groovy.util.logging.Slf4j;
import jakarta.inject.Singleton;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
            while (true) {
                try {
                    System.out.println("RTMP server is listening on port 1935...");
                    try (Socket socket = serverSocket.accept()) {
                        socket.setTcpNoDelay(true);
                        this.connectionStartTime = System.currentTimeMillis();
                        handleHandShake(socket.getInputStream(), socket.getOutputStream());
                        InputStream inputStream = socket.getInputStream();
                        OutputStream outputStream = socket.getOutputStream();

                        while (!socket.isClosed()) {
                            if (inputStream.available() > 0) {
                                handleChunkMessage(inputStream, outputStream);
                            } else {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } catch (Exception ex) {
                    System.out.println("Exception while handle chunk message: " + ex);
                    ex.printStackTrace();
                    break;
                }
            }
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
                currentMessageData.flip();
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
                        List<Object> messages = new ArrayList<>();
                        while (currentMessageData.hasRemaining()) {
                            final Object message = decodeAMF0CommandMessage(currentMessageData);
                            messages.add(message);
                        }
                        System.out.println("Finished decoding AMF0 command message: " + messages);
                        handleCommandMessage(messages, inputStream, outputStream);
                        break;
                }
            }
        }

        chunkPayload.remove(header.basic.csid);
    }

    private void handleCommandMessage(List<Object> messages,
                                      InputStream inputStream,
                                      OutputStream outputStream) throws IOException {
        if (messages.isEmpty()) {
            System.out.println("Empty command message, exit.");
            return;
        }
        String command = messages.getFirst().toString();
        switch (command) {
            case "connect":
                if (messages.get(2) instanceof Map<?, ?> map) {
                    String clientName = map.get("app").toString();
                    System.out.println("Processing connect message for client: " + clientName);

                    int encodeRequest = map.containsKey("objectEncoding") ?
                            Integer.parseInt(map.get("objectEncoding").toString()) : 0; // should be 0 or 3
                    if (encodeRequest == 3) {
                        System.out.println("WARNING: AMF3 is not supported, exit.");
                        return;
                    }

                    // send window ack size - 4 bytes
                    final byte[] messageHeader = constructMessageHeader(1, 1);
                    outputStream.write(ByteBuffer.allocate(4).putInt(5000000).array());

                    // send peer bandwidth
                    final ByteBuffer peerBandWidth = ByteBuffer.allocate(5);
                    peerBandWidth.putInt(50000000);// SOFT
                    peerBandWidth.put((byte) 2);
                    outputStream.write(peerBandWidth.array());

//                    // wait for window ack size, but OBS doesn't send!
//                    while (inputStream.available() == 0) {
//                        System.out.println("Waiting for window ack size...");
//                    }

                    // send command message (_result)
                    List<Object> result = new ArrayList<>();
                    result.add("_result");
                    result.add(Double.parseDouble("1.0")); // transaction id
                    result.add(new AMF0Message(new ArrayList<>(Arrays.asList(
                                    new Tuple2<>("fmsVer", "FMS/3,0,1,123"),
                                    new Tuple2<>("capabilities", 31)
                            )))
                    );
                    result.add(new AMF0Message(new ArrayList<>(Arrays.asList(
                                    new Tuple2<>("level", "status"),
                                    new Tuple2<>("code", "NetConnection.Connect.Success"),
                                    new Tuple2<>("description", "Connection succeeded"),
                                    new Tuple2<>("objectEncoding", 0)
                            )))
                    );
                    final byte[] resultCommandMessage = encodeAMF0CommandMessage(result);
                    outputStream.write(resultCommandMessage);
                }
                System.out.println("Finished processing connect message");
                break;
            case "createStream":
                System.out.println("Processing Create Stream message...");
                break;
            default:
                System.out.println("Unknown command: " + command);
                break;
        }
    }

    private byte[] constructMessageHeader(int csID) {
        return constructMessageHeader(csID, 0);
    }

    private byte[] constructMessageHeader(int csID, int fmt) {
        byte[] basicHeader = new byte[2];
        if(csID == 0){
            basicHeader =
        }
    }

    byte[] encodeAMF0CommandMessage(final List<Object> messages) {
        ByteBuffer buffer = ByteBuffer.allocate(256);
        if (messages.isEmpty()) {
            System.out.println("Empty command message, exit.");
            return new byte[0];
        }

        for (Object message : messages) {
            switch (message) {
                case Double doubleVal:
                    buffer.put((byte) 0);
                    buffer.putLong(Double.doubleToLongBits(doubleVal));
                    break;
                case String stringVal:
                    buffer.put((byte) 2);
                    buffer.put(encodeStringVal(stringVal));
                    break;
                case AMF0Message amf0Message:
                    buffer.put((byte) 3);
                    amf0Message.forEach((key, value) -> {
                        buffer.put(encodeStringVal(key));
                        if (value instanceof String) {
                            buffer.put((byte) 2);
                            buffer.put(encodeStringVal((String) value));
                        } else if (value instanceof Integer) {
                            buffer.put((byte) 0);
                            buffer.putLong(Double.doubleToLongBits(Double.parseDouble(value.toString())));
                        } else {
                            System.out.println("Not supported value type");
                            throw new RuntimeException("Not supported value type");
                        }
                    });
                    buffer.put(AMF0Message.OBJECT_END_MARKER);
                    break;
                default:
                    System.out.println("Not supported value type");
                    break;
            }
        }

        return buffer.array();
    }

    private byte[] encodeStringVal(String stringVal) {
        byte[] stringValBytes = stringVal.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(stringValBytes.length + 2);
        buffer.putShort((short) stringValBytes.length);
        buffer.put(stringValBytes);
        return buffer.array();
    }

    Object decodeAMF0CommandMessage(ByteBuffer currentMessageData) {
        int encodedType = Byte.toUnsignedInt(currentMessageData.get());
        switch (encodedType) {
            case 2, 0x0C: //STRING, LONG STRING
                int length;
                if (encodedType == 0x02) {
                    length = currentMessageData.getShort();
                } else {
                    length = currentMessageData.getInt();
                }
                return AMF0Decoder.getString(currentMessageData, length);
            case 0: //NUMBER
                return Double.longBitsToDouble(currentMessageData.getLong());
            case 0x08: // MAP
            case 3: // OBJECT
                Map<String, Object> map = new LinkedHashMap<>();
                int count = 0;
                if (encodedType == 0x08) {
                    count = currentMessageData.getInt();
                }
                System.out.println("Process AMF3 Command message, count: " + count);
                int i = 0;
                final byte[] endMarker = new byte[3];
                while (currentMessageData.hasRemaining()) {
                    currentMessageData.get(currentMessageData.position(), endMarker);
                    if (Arrays.equals(endMarker, AMF0Message.OBJECT_END_MARKER)) {
                        currentMessageData.get(new byte[3]); // skip 3 bytes
                        System.out.println("Finished reading for MAP/OBJECT data when reaching end marker");
                        break;
                    }
                    if (count > 0 && i == count) {
                        System.out.println("Finished reading for MAP/OBJECT data");
                        break;
                    }
                    short propertySize = currentMessageData.getShort();
                    byte[] keyArr = new byte[propertySize];
                    currentMessageData.get(keyArr);
                    String key = new String(keyArr);
                    Object value = decodeAMF0CommandMessage(currentMessageData);
                    map.put(key, value);
                    i++;
                }
                return map;
            case 0x01: //NUMBER
                break;
            case 0x0A: //ARRAY
                break;
            case 0x0B: //DATE
                break;
            case 0x05: //NULL
                break;
            case 0x06: //UNDEFINED
                break;
            case 0x0D: //UNSUPPORTED
                return null;
            default:
                return null;
        }

        return null;
    }

    /**
     * Read and parse RTMP header (with basic header and message header) from input
     * stream
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
            csid = (inputStream.readNBytes(1)[0] & MASK_OF_8_BITS) + 64;
        } else if (csid == 1) { // 3 bytes header form
            byte[] bytes = inputStream.readNBytes(2);
            csid = ((bytes[0] & MASK_OF_8_BITS) + 64) + ((bytes[1] & MASK_OF_8_BITS) << 8);
        }
        basicHeader.csid = csid;
        System.out.println("Reading chunk " + basicHeader.csid);

        final Message message = rtmpHeader.message;
        switch (basicHeader.fmt) {
            case 0: // 11 bytes
                message.timestamp = readIntFrom3Bytes(inputStream);
                message.length = readIntFrom3Bytes(inputStream);
                message.typeId = inputStream.read();
                message.streamId = ByteBuffer.wrap(inputStream.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
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

