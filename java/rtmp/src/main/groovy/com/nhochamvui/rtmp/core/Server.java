package com.nhochamvui.rtmp.core;

import groovy.util.logging.Slf4j;
import jakarta.inject.Singleton;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

@Singleton
@Slf4j
public class Server {

    public static final int HANDSHAKE_LENGTH = 1536;
    public static final int VERSION_LENGTH = 1;
    public static final int DEFAULT_VERSION = 3;

    private static final Random randomSeed = new Random();

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(1935, 50, InetAddress.getByName("127.0.0.1"))) {
            serverSocket.setReuseAddress(true);
            System.out.println("RTMP server is listening on port 1935...");
            Socket socket = serverSocket.accept();

            handleHandShake(socket.getInputStream(), socket.getOutputStream());

            socket.close();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
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
                System.out.println("Selected " + DEFAULT_VERSION + " version.");
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
        final long then = System.currentTimeMillis();
        if (in.read(buffer) == -1) {
            throw new IOException("End of stream");
        }
        final long now = System.currentTimeMillis();
        System.out.println("Finish in "+ ((now - then)/1000));
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
        System.out.println("Finish in "+ ((now - then)/1000));
        return buffer;
    }
}
