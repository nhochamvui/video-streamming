package com.nhochamvui.rtmp.core;

import com.nhochamvui.rtmp.core.enums.AMF0Type;
import com.nhochamvui.rtmp.core.enums.ReadState;
import com.nhochamvui.rtmp.core.exceptions.StreamClose;
import com.nhochamvui.rtmp.core.models.AMF0Message;
import com.nhochamvui.rtmp.core.models.Basic;
import com.nhochamvui.rtmp.core.models.Message;
import com.nhochamvui.rtmp.core.models.RTMPHeader;
import groovy.lang.Tuple2;
import groovy.util.logging.Slf4j;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import jakarta.inject.Singleton;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.nhochamvui.rtmp.core.constants.Constant.*;
import static com.nhochamvui.rtmp.core.functions.MediaHandler.createFlvHeader;
import static com.nhochamvui.rtmp.core.functions.MediaHandler.writeFlvTag;


@Singleton
public class Server {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Server.class);
    private static final org.slf4j.Logger ffmpegLog = org.slf4j.LoggerFactory.getLogger("ffmpeg");

    public static final int HANDSHAKE_LENGTH = 1536;
    public static final int VERSION_LENGTH = 1;
    public static final int DEFAULT_VERSION = 3;

    private static final Random randomSeed = new Random();

    private ReadState readState;
    private int inChunkSize = 128;
    private int outChunkSize = 128;
    private long connectionStartTime;
    private String connectionId;
    private String connectionIp;
    private String streamKey;
    private int nextStreamId = 1;

    /**
     * Contains previous headers grouped by chunk stream ID
     */
    private final Map<Integer, RTMPHeader> prevHeaders = new HashMap<>();

    /**
     * Payload grouped by chunk stream ID
     */
    private final Map<Integer, ByteBuffer> chunkPayload = new HashMap<>();

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(1935, 50, InetAddress.getByName("0.0.0.0"))) {
            serverSocket.setReuseAddress(true);
            while (true) {
                try {
                    log.info("RTMP server is listening on port 1935...");
                    try (Socket socket = serverSocket.accept()) {
                        socket.setTcpNoDelay(true);
                        socket.setSoTimeout(5000);
                        this.connectionStartTime = System.currentTimeMillis();
                        this.connectionIp = socket.getInetAddress().getHostAddress();
                        this.connectionId = connectionIp + "-" + connectionStartTime;
                        log.info("[{}] RTMP connection accepted from {}", connectionId, connectionIp);

                        InputStream inputStream = socket.getInputStream();
                        OutputStream outputStream = socket.getOutputStream();
                        handleHandShake(inputStream, outputStream);

                        while (!socket.isClosed() && !socket.isInputShutdown()) {
                            if (inputStream.available() > 0) {
                                try {
                                    handleChunkMessage(inputStream, outputStream);
                                }
                                catch (StreamClose e) {
                                    socket.close();
                                }
                            } else {
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }

                        if (process != null) {
                            process.getOutputStream().close();
                            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                                process.destroyForcibly();
                                process.waitFor(3, TimeUnit.SECONDS);
                            }
                            if (process.isAlive()) {
                                log.info("[{}] FFmpeg still alive after cleanup, ignoring", connectionId);
                            } else {
                                log.info("[{}] FFmpeg process finished with exit code: {}", connectionId, process.exitValue());
                            }
                            process = null;
                            sentFlvHeader = false;
                        }

                        long duration = System.currentTimeMillis() - connectionStartTime;
                        log.info("[{}] RTMP connection closed | duration={}ms | IP={}", connectionId, duration, connectionIp);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } catch (Exception ex) {
                    log.error("[{}] Connection ended: {}", connectionId != null ? connectionId : "?", ex.getMessage(), ex);
                }
            }
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage(), e);
        }
    }

    public void setStreamKey(String key) {
        this.streamKey = key != null && !key.isEmpty() ? key : System.getenv("RTMP_STREAM_KEY");
    }

    List<String> command = List.of("ffmpeg",
            "-fflags", "+genpts",
            "-f", "flv", "-i", "pipe:0",
            "-map", "0:v", "-map", "0:a", "-c", "copy",
            "-f", "hls",
            "-hls_time", "1",
            "-hls_segment_type", "fmp4",
            "-hls_list_size", "10",
            "-hls_flags", "delete_segments+split_by_time",
            "-hls_segment_filename", "hls/hd/output_%d.m4s",
            "hls/hd/output.m3u8",
            "-map", "0:v", "-map", "0:a",
            "-c:v", "libx264", "-preset", "ultrafast", "-b:v", "800k",
            "-s", "854x480", "-g", "30",
            "-c:a", "aac", "-b:a", "64k",
            "-f", "hls",
            "-hls_time", "1",
            "-hls_segment_type", "fmp4",
            "-hls_list_size", "10",
            "-hls_flags", "delete_segments+split_by_time",
            "-hls_segment_filename", "hls/ld/output_%d.m4s",
            "hls/ld/output.m3u8");
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    Process process = null;
    boolean sentFlvHeader = false;

    /**
     * Handle reading a chunk message
     *
     * @param inputStream
     * @param outputStream
     * @throws IOException
     */
    private void handleChunkMessage(InputStream inputStream, OutputStream outputStream) throws IOException, StreamClose {
        final RTMPHeader header = readRTMPHeader(inputStream);
        prevHeaders.put(header.basic.csid, header);
        chunkPayload.putIfAbsent(header.basic.csid, ByteBuffer.allocateDirect(header.message.length));
        ByteBuffer currentMessageData = chunkPayload.get(header.basic.csid);
        assert currentMessageData != null;
        if (currentMessageData.hasRemaining()) {
            int maxLengthForCurrentChunkData = Math.min(currentMessageData.remaining(), inChunkSize);
            currentMessageData.put(inputStream.readNBytes(maxLengthForCurrentChunkData));
            if (!currentMessageData.hasRemaining()) {
                currentMessageData.flip();

                chunkPayload.remove(header.basic.csid);
                List<Object> messages = new ArrayList<>();
                switch (header.message.typeId) {
                    case 1:
                        this.inChunkSize = currentMessageData.getInt(0);
                        log.info("[{}] Process SetChunkSize message, new inChunkSize: {}", connectionId, this.inChunkSize);
                        break;
                    case 2:
                        log.info("[{}] Process AbortMessage message", connectionId);
                        break;
                    case 3:
                        log.info("[{}] Process Acknowledgement message", connectionId);
                        break;
                    case 4:
                        log.info("[{}] Process UserControl message", connectionId);
                        break;
                    case 5:
                        log.info("[{}] Process Window Acknowledgement Size message", connectionId);
                        break;
                    case 6:
                        log.info("[{}] Process Set Peer Bandwidth message", connectionId);
                        break;
                    case 8:
                    case 9:
                        byte[] payload = new byte[currentMessageData.remaining()];
                        currentMessageData.get(0, payload);

                        if (process == null){
                            new File("hls/hd").mkdirs();
                            new File("hls/ld").mkdirs();
                            writeMasterPlaylist();
                            processBuilder.redirectErrorStream(true);
                            process = processBuilder.start();

                            Thread.ofVirtual().start(() -> {
                                try {
                                    java.io.BufferedReader reader = new java.io.BufferedReader(
                                            new java.io.InputStreamReader(process.getInputStream()));
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        ffmpegLog.info("[{}] {}", connectionId, line);
                                    }
                                } catch (IOException ignored) { }
                            });
                        }

                        if (!process.isAlive()) {
                            String error = new String(process.getErrorStream().readAllBytes());
                            log.warn("[{}] FFmpeg exited: {}, error: {}", connectionId, process.exitValue(), error);
                            sentFlvHeader = false;
                            process = null;
                            break;
                        }

                        if (header.message.typeId == 9 && !sentFlvHeader) {
                            if (payload.length < 2 || (payload[1] & 0xFF) != 0) {
                                log.info("[{}] Skip first video frame: not a sequence header", connectionId);
                                break;
                            }
                        }

                        if (!sentFlvHeader){
                            sentFlvHeader = true;
                            process.getOutputStream().write(createFlvHeader());
                        }

                        writeFlvTag(process.getOutputStream(), (byte)header.message.typeId, header.message.timestamp, payload);
                        break;
                    case 15:
                        log.info("[{}] Process AMF3 Command message", connectionId);
                        break;
                    case 18:
                        log.info("[{}] Process metadata message", connectionId);
                        break;
                    case 20:
                        log.info("[{}] Process AMF0 Command message", connectionId);
                        while (currentMessageData.hasRemaining()) {
                            final Object message = decodeAMF0CommandMessage(currentMessageData);
                            messages.add(message);
                        }
                        log.info("[{}] Finished decoding AMF0 command message: {}", connectionId, messages);
                        String commandName = messages.isEmpty() ? "UNKNOWN" : messages.getFirst().toString();
                        log.info("[{}] >>> Received command: {}", connectionId, commandName);
                        handleCommandMessage(messages, header.message.streamId, inputStream, outputStream);
                        break;
                }
            }
        }
    }

    private void handleCommandMessage(List<Object> messages,
            int messageStreamId,
            InputStream inputStream,
            OutputStream outputStream) throws IOException, StreamClose {
        if (messages.isEmpty()) {
            log.warn("[{}] Empty command message, exit.", connectionId);
            return;
        }
        String command = messages.getFirst().toString();
        log.info("[{}] >>> Handling command: {} on stream {}", connectionId, command, messageStreamId);
        switch (command) {
            case "connect":
                if (messages.get(2) instanceof Map<?, ?> map) {
                    String clientName = map.get("app").toString();
                    log.info("[{}] Processing connect message for client: {}", connectionId, clientName);

                    int encodeRequest = map.containsKey("objectEncoding")
                            ? Integer.parseInt(map.get("objectEncoding").toString())
                            : 0;
                    if (encodeRequest == 3) {
                        log.warn("[{}] WARNING: AMF3 is not supported, exit.", connectionId);
                        return;
                    }

                    this.outChunkSize = 5000;
                    sendWindowAckSize(outputStream, 5000000);
                    sendSetPeerBandwidth(outputStream, 5000000, 2);
                    sendSetChunkSize(outputStream, this.outChunkSize);

                    List<Object> responseBody = new ArrayList<>();
                    responseBody.add("_result");
                    responseBody.add(1); // transaction id always = 1
                    responseBody.add(new AMF0Message(new ArrayList<>(Arrays.asList(
                            new Tuple2<>("fmsVer", "FMS/3,0,1,123"),
                            new Tuple2<>("capabilities", 31)))));
                    responseBody.add(new AMF0Message(new ArrayList<>(Arrays.asList(
                            new Tuple2<>("level", "status"),
                            new Tuple2<>("code", "NetConnection.Connect.Success"),
                            new Tuple2<>("description", "Connection succeeded"),
                            new Tuple2<>("objectEncoding", 0)))));
                    final byte[] resultCommandMessage = encodeAMF0CommandMessage(responseBody, 0);
                    log.info("[{}] before sending result: {} available", connectionId, inputStream.available());
                    outputStream.write(resultCommandMessage);
                    log.info("[{}] Result message bytes: {} bytes", connectionId, resultCommandMessage.length);

                    outputStream.flush();
                }
                log.info("[{}] Finished processing connect message", connectionId);
                break;

            case "releaseStream":
                log.info("[{}] Processing releaseStream...", connectionId);
                {
                    double txId = messages.size() > 1 && messages.get(1) instanceof Number
                            ? ((Number) messages.get(1)).doubleValue() : 0;
                    List<Object> response = Arrays.asList("_result", txId, null);
                    byte[] data = encodeAMF0CommandMessage(response, 0);
                    outputStream.write(data);
                    outputStream.flush();
                }
                break;

            case "FCPublish":
                log.info("[{}] Processing FCPublish...", connectionId);
                {
                    double txId = messages.size() > 1 && messages.get(1) instanceof Number
                            ? ((Number) messages.get(1)).doubleValue() : 0;
                    List<Object> response = Arrays.asList("_result", txId, null);
                    byte[] data = encodeAMF0CommandMessage(response, 0);
                    outputStream.write(data);
                    outputStream.flush();
                }
                break;

            case "FCUnpublish":
                log.info("[{}] Processing FCUnpublish...", connectionId);
                {
                    double txId = messages.size() > 1 && messages.get(1) instanceof Number
                            ? ((Number) messages.get(1)).doubleValue() : 0;
                    List<Object> response = Arrays.asList("_result", txId, null);
                    byte[] data = encodeAMF0CommandMessage(response, 0);
                    outputStream.write(data);
                    outputStream.flush();
                }
                break;

            case "createStream":
                log.info("[{}] Processing createStream...", connectionId);
                {
                    double txId = messages.size() > 1 && messages.get(1) instanceof Number
                            ? ((Number) messages.get(1)).doubleValue() : 0;
                    int newStreamId = nextStreamId++;
                    log.info("[{}] Allocated stream ID: {}", connectionId, newStreamId);
                    List<Object> response = Arrays.asList("_result", txId, null, (double) newStreamId);
                    byte[] data = encodeAMF0CommandMessage(response, 0);
                    outputStream.write(data);
                    outputStream.flush();
                }
                break;

            case "publish":
                log.info("[{}] Processing publish...", connectionId);
                {
                    String streamName = messages.size() > 3 && messages.get(3) != null
                            ? messages.get(3).toString() : "stream";
                    String streamType = messages.size() > 4 && messages.get(4) != null
                            ? messages.get(4).toString() : "live";
                    log.info("[{}] Stream: {}, type: {}", connectionId, streamName, streamType);

                    String activeKey = streamKey != null && !streamKey.isEmpty()
                            ? streamKey : System.getenv("RTMP_STREAM_KEY");
                    if (activeKey != null && !activeKey.isEmpty() && !activeKey.equals(streamName)) {
                        log.warn("[{}] Invalid stream key '{}', expected '{}'", connectionId, streamName, activeKey);
                        Map<String, Object> errInfo = new LinkedHashMap<>();
                        errInfo.put("level", "error");
                        errInfo.put("code", "NetStream.Publish.BadName");
                        errInfo.put("description", "Invalid stream key");
                        List<Object> errResult = Arrays.asList("onStatus", (double) 0, null, errInfo);
                        byte[] errData = encodeAMF0CommandMessage(errResult, messageStreamId);
                        outputStream.write(errData);
                        outputStream.flush();
                        throw new StreamClose();
                    }

                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("level", "status");
                    info.put("code", "NetStream.Publish.Start");
                    info.put("description", "Stream is now published");
                    List<Object> statusResult = Arrays.asList("onStatus", (double) 0, null, info);
                    byte[] data = encodeAMF0CommandMessage(statusResult, messageStreamId);
                    outputStream.write(data);
                    outputStream.flush();
                }
                break;
            case "deleteStream":
                log.info("[{}] Stop stream, clear session", connectionId);
                this.chunkPayload.clear();
                throw new StreamClose();
            default:
                log.warn("[{}] Unknown command: {}", connectionId, command);
                break;
        }
    }

    private byte[] constructMessageHeader(int csID) {
        return constructMessageHeader(csID, 0);
    }

    private byte[] constructMessageHeader(int csID, int fmt) {
        ByteBuf buffer = Unpooled.buffer();

        return buffer.array();
    }

    /**
     * Send Set Peer Bandwidth message (Type 6)
     * This sets the bandwidth limit for the peer
     * @param limitType
     * 0 — Hard: The peer must strictly limit its output bandwidth.
     * 1 — Soft: The peer can limit its bandwidth, or use the window size already in effect, whichever is smaller.
     * 2 — Dynamic: If the previous limit was Hard, treat this as Hard. Otherwise, ignore it.
     */
    private void sendSetPeerBandwidth(OutputStream out, int bandwidth, int limitType) throws IOException {
        ByteBuf buffer = Unpooled.buffer();

        // Basic Header: fmt=0, csid=2
        buffer.writeByte(0x02);

        // Message Header (11 bytes for fmt 0)
        buffer.writeMedium((int) (System.currentTimeMillis() - connectionStartTime));
        buffer.writeMedium(5); // message length = 5 bytes
        buffer.writeByte(6); // message type = Set Peer Bandwidth
        buffer.writeIntLE(0); // stream ID = 0 (protocol control)

        // Payload (5 bytes)
        buffer.writeInt(bandwidth);
        buffer.writeByte((byte) limitType);

        // Write to output stream
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        out.write(data);
        out.flush();

        buffer.release();
        log.info("[{}] Sent Set Peer Bandwidth: {}, type: {} ({} bytes)", connectionId, bandwidth, limitType, data.length);
    }

    private void sendWindowAckSize(OutputStream out, int windowSize) throws IOException {
        ByteBuf buffer = Unpooled.buffer();

        // Basic Header: fmt=0, cs id = 2
        buffer.writeByte(0x02);

        // Message Header (11 bytes for fmt 0)
        buffer.writeMedium((int) (System.currentTimeMillis() - connectionStartTime)); // 3 bytes for timestamp
        buffer.writeMedium(4); // message length = 4 bytes
        buffer.writeByte(5); // message type = Window Ack Size
        buffer.writeIntLE(0); // stream ID = 0 (protocol control)

        // Payload (4 bytes)
        buffer.writeInt(windowSize);

        // Write to output stream
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        out.write(data);
        out.flush();

        buffer.release();
        log.info("[{}] Sent Window Ack Size: {} ({} bytes)", connectionId, windowSize, data.length);
    }

    private void sendSetChunkSize(OutputStream outputStream, int chunkSize) throws IOException {
        ByteBuf buffer = Unpooled.buffer();

        // Basic Header: fmt=0, csid=2
        buffer.writeByte(0x02);

        // Message Header (11 bytes for fmt 0)
        buffer.writeMedium((int) (System.currentTimeMillis() - connectionStartTime));
        buffer.writeMedium(4); // message length = 4 bytes
        buffer.writeByte(1); // message type = set chunk size
        buffer.writeIntLE(0); // stream ID = 0 (protocol control)

        // Payload (4 bytes)
        buffer.writeInt(chunkSize);

        // Write to output stream
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        outputStream.write(data);
        outputStream.flush();

        buffer.release();
        log.info("[{}] Sent set chunk size message: {} ({} bytes)", connectionId, chunkSize, data.length);
    }

    private static void writeMasterPlaylist() throws IOException {
        String playlist = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080,NAME="HD"
                hd/output.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480,NAME="SD"
                ld/output.m3u8
                """;
        Files.writeString(java.nio.file.Path.of("hls/master.m3u8"), playlist);
        log.info("Written master playlist");
    }

    byte[] encodeAMF0CommandMessage(final List<Object> messages, int streamId) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        if (messages.isEmpty()) {
            log.warn("Empty command message, exit.");
            return new byte[0];
        }

        // header: any value other than 0, 1, 2 for command message
        int csid = 3;
        byte[] basicHeader = encodeBasicHeader(0, csid);
        buffer.writeBytes(basicHeader);
        long timestamp = System.currentTimeMillis() - connectionStartTime;
        int maxTimestamp = 0xFFFFFF;
        boolean needExtraTime = false;
        if (timestamp >= maxTimestamp) {
            needExtraTime = true;
            buffer.writeMedium(maxTimestamp);
        } else {
            buffer.writeMedium((int) timestamp);
        }

        // payload
        ByteBuf encodedPayload = encodeRTMPCommandMessagePayload(messages);

        //message header
        buffer.writeMedium(encodedPayload.readableBytes()); // message length
        buffer.writeByte(MSG_TYPE_COMMAND_AMF0); // message type
        buffer.writeIntLE(streamId); // message stream id

        if (needExtraTime) {
            buffer.writeInt((int) timestamp);
        }

        ByteBuf completeMessage = Unpooled.buffer();

        boolean fmt0Part = true;
        while (encodedPayload.isReadable()) {
            int min = Math.min(outChunkSize, encodedPayload.readableBytes());
            if (fmt0Part) {
                buffer.writeBytes(encodedPayload, min);
                fmt0Part = false;
            } else {
                byte[] fmt3BasicHeader = encodeBasicHeader(CHUNK_FMT_3, csid);
                buffer.writeBytes(fmt3BasicHeader);
                buffer.writeBytes(encodedPayload, min);
            }
            completeMessage.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
            buffer.release();
            buffer = Unpooled.buffer();
        }
        encodedPayload.release();
        byte[] result = new byte[completeMessage.readableBytes()];
        completeMessage.readBytes(result);
        completeMessage.release();
        return result;
    }

    private static ByteBuf encodeRTMPCommandMessagePayload(final List<Object> messages) {
        ByteBuf byteBuffer = Unpooled.buffer();
        for (Object message : messages) {
            byteBuffer.writeBytes(encodeMessage(message));
        }

        return byteBuffer;
    }

    private static ByteBuf encodeMessage(final Object message) {
        log.info("Encoding: {} type: {}", message, message != null ? message.getClass() : "null");
        ByteBuf byteBuffer = Unpooled.buffer();
        if (message == null) {
            byteBuffer.writeByte((byte) 0x05);
            return byteBuffer;
        }
        switch (message) {
            case Number numVal:
                byteBuffer.writeByte((byte) AMF0Type.NUMBER.value);
                byteBuffer.writeLong(Double.doubleToLongBits(Double.parseDouble(numVal.toString())));
                break;
            case String stringVal:
                byteBuffer.writeByte((byte) AMF0Type.STRING.value);
                byteBuffer.writeBytes(encodeStringVal(stringVal));
                break;
            case Map<?, ?> mapVal:
                byteBuffer.writeByte((byte) AMF0Type.OBJECT.value);
                mapVal.forEach((key, value) -> {
                    byteBuffer.writeBytes(encodeStringVal(key.toString()));
                    byteBuffer.writeBytes(encodeMessage(value));
                });
                byteBuffer.writeBytes(AMF0Message.OBJECT_END_MARKER);
                break;
            default:
                log.warn("Not supported value type: {}", message.getClass());
                break;
        }
        return byteBuffer;
    }

    private static byte[] encodeBasicHeader(final int fmt, final int csid) {
        if (csid >= 2 && csid <= 63) {
            return new byte[] { (byte) ((fmt << 6) + csid) };
        } else if (csid >= 64 && csid <= 319) {
            return new byte[] { (byte) (fmt << 6), (byte) (csid - 64) };
        } else {
            // little Endian
            return new byte[] { (byte) ((fmt << 6) | 1), (byte) ((csid - 64) & 0xff), (byte) ((csid - 64) >> 8) };
        }
    }


    // Only supports AMF0 short strings (type 0x02, max 65535 bytes).
    // Strings longer than 65535 bytes are truncated to 65535 bytes.
    // Full support would need LONG_STRING (type 0x0C) with a 4-byte length prefix.
    private static byte[] encodeStringVal(String stringVal) {
        byte[] stringValBytes = stringVal.getBytes();
        int length = Math.min(stringValBytes.length, 65535);
        ByteBuffer buffer = ByteBuffer.allocate(length + 2);
        buffer.putShort((short) length);
        buffer.put(stringValBytes, 0, length);
        return buffer.array();
    }

    Object decodeAMF0CommandMessage(ByteBuffer currentMessageData) {
        int encodedType = Byte.toUnsignedInt(currentMessageData.get());
        switch (encodedType) {
            case 0x02, 0x0C: // STRING, LONG STRING
                int length;
                if (encodedType == 0x02) {
                    length = currentMessageData.getShort();
                } else {
                    length = currentMessageData.getInt();
                }
                return AMF0Decoder.getString(currentMessageData, length);
            case 0: // NUMBER
                return Double.longBitsToDouble(currentMessageData.getLong());
            case 0x08: // MAP
            case 3: // OBJECT
                Map<String, Object> map = new LinkedHashMap<>();
                int count = 0;
                if (encodedType == 0x08) {
                    count = currentMessageData.getInt();
                }
                log.info("[{}] Process AMF0 MAP/OBJECT message, count: {}", connectionId, count);
                int i = 0;
                final byte[] endMarker = new byte[3];
                while (currentMessageData.hasRemaining()) {
                    currentMessageData.get(currentMessageData.position(), endMarker);
                    if (Arrays.equals(endMarker, AMF0Message.OBJECT_END_MARKER)) {
                        currentMessageData.get(new byte[3]); // skip 3 bytes
                        log.info("[{}] Finished reading MAP/OBJECT data when reaching end marker", connectionId);
                        break;
                    }
                    if (count > 0 && i == count) {
                        log.info("[{}] Finished reading MAP/OBJECT data", connectionId);
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
            case 0x01: // NUMBER
                break;
            case 0x0A: // ARRAY
                break;
            case 0x0B: // DATE
                break;
            case 0x05: // NULL
                break;
            case 0x06: // UNDEFINED
                break;
            case 0x0D: // UNSUPPORTED
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

        final Message message = rtmpHeader.message;
        switch (basicHeader.fmt) {
            case 0: // 11 bytes
                message.timestamp = readIntFrom3Bytes(inputStream);
                if (message.timestamp == 0xFFFFFF) {
                    message.timestamp = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();
                }
                message.length = readIntFrom3Bytes(inputStream);
                message.typeId = inputStream.read();
                message.streamId = ByteBuffer.wrap(inputStream.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
                break;
            case 1: // 7 bytes
                message.timestampDelta = readIntFrom3Bytes(inputStream);
                if (message.timestampDelta == 0xFFFFFF) {
                    message.timestampDelta = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();
                }
                message.length = readIntFrom3Bytes(inputStream);
                message.typeId = inputStream.read();
                RTMPHeader prev1 = prevHeaders.get(basicHeader.csid);
                if (prev1 != null) {
                    message.streamId = prev1.message.streamId;
                    message.timestamp = prev1.message.timestamp + message.timestampDelta;
                } else {
                    log.warn("[{}] WARN: fmt=1 for unknown csid={}", connectionId, basicHeader.csid);
                }
                break;
            case 2: // 3 bytes
                message.timestampDelta = readIntFrom3Bytes(inputStream);
                if (message.timestampDelta == 0xFFFFFF) {
                    message.timestampDelta = ByteBuffer.wrap(inputStream.readNBytes(4)).getInt();
                }
                RTMPHeader prev2 = prevHeaders.get(basicHeader.csid);
                if (prev2 != null) {
                    message.length = prev2.message.length;
                    message.typeId = prev2.message.typeId;
                    message.streamId = prev2.message.streamId;
                    message.timestamp = prev2.message.timestamp + message.timestampDelta;
                } else {
                    log.warn("[{}] WARN: fmt=2 for unknown csid={}", connectionId, basicHeader.csid);
                }
                break;
            case 3: // 0 bytes
                RTMPHeader prev3 = prevHeaders.get(basicHeader.csid);
                if (prev3 != null) {
                    message.timestampDelta = prev3.message.timestampDelta;
                    message.timestamp = prev3.message.timestamp;
                    message.length = prev3.message.length;
                    message.typeId = prev3.message.typeId;
                    message.streamId = prev3.message.streamId;
                } else {
                    log.warn("[{}] WARN: fmt=3 for unknown csid={}", connectionId, basicHeader.csid);
                }
                break;
        }

        return rtmpHeader;
    }

    public static void handleHandShake(InputStream reader, OutputStream out) throws IOException {
        log.info("Handshake started.");

        // C0 is a byte indicates the requested version from RTMP client
        int c0 = reader.read();
        byte[] c1 = readToBuffer(reader, HANDSHAKE_LENGTH);
        sendS0S1(out, c0);
        sendS2(out, c1);
        byte[] c2 = readToBuffer(reader, HANDSHAKE_LENGTH);

        log.info("Handshake finished.");
    }

    private static void sendS2(OutputStream out, byte[] c1) throws IOException {
        byte[] c1Time = Arrays.copyOfRange(c1, 0, 3);
        byte[] c1RandBytes = Arrays.copyOfRange(c1, 8, c1.length);
        byte[] s2 = new byte[HANDSHAKE_LENGTH];
        System.arraycopy(c1Time, 0, s2, 0, c1Time.length);
        // TODO: should fill C1 reception timestamp from 4 to 7 index
        System.arraycopy(c1RandBytes, 0, s2, 8, c1RandBytes.length);
        out.write(s2);
    }

    public static int determineVersion(int requestedVersion) {
        switch (requestedVersion) {
            case 0, 2:
                log.warn("Reject deprecated version.");
            default:
                log.info("Selected version: {}", DEFAULT_VERSION);
                return DEFAULT_VERSION;
        }
    }

    private static void sendS0S1(OutputStream out, int c0) throws IOException {
        // S0 - version
        int s0 = determineVersion(c0);

        // S1: 1536 bytes total
        // bytes 0-3: server timestamp (0)
        // bytes 4-7: zero (simple handshake)
        // bytes 8-1535: random data (1528 bytes)
        byte[] s1 = new byte[HANDSHAKE_LENGTH];
        byte[] randomData = generateRandomBytes(HANDSHAKE_LENGTH - 8);
        System.arraycopy(randomData, 0, s1, 8, randomData.length);

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
        log.info("Finish in {} seconds", (now - then) / 1000);
        return buffer;
    }

    private int readIntFrom3Bytes(InputStream inputStream) throws IOException {
        byte[] threeBytes = inputStream.readNBytes(3);
        byte[] fourBytes = new byte[4];
        System.arraycopy(threeBytes, 0, fourBytes, 1, threeBytes.length);
        return ByteBuffer.wrap(fourBytes).getInt();
    }
}
