package com.nhochamvui.rtmp.core;

import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

@Singleton
public class Server {

    public static final int HANDSHAKE_LENGTH = 1536;
    public static final int VERSION_LENGTH = 1;

    private static final Random randomSeed = new Random();

    public void listen() {
        try (ServerSocket serverSocket = new ServerSocket(1935, 50, InetAddress.getByName("127.0.0.1"))) {
            serverSocket.setReuseAddress(true);
            System.out.println("RTMP server is listening on port 1935...");
            Socket socket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            handleHandShake(in, socket.getOutputStream());

            socket.close();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    public static void handleHandShake(BufferedReader in, OutputStream out) throws IOException {
        System.out.println("Handshake received");
        byte[] c1 = readToBuffer(in);
        sendS0S1S2(out);
        byte[] c2 = readToBuffer(in);
        System.out.println("Handshake finished");
    }

    private static void sendS0S1S2(OutputStream out) throws IOException {
        // version
        out.write(3);
        // s1 time
        out.write(0);
        out.write(0);
        // s1 random bytes
        out.write(generateRandomBytes(HANDSHAKE_LENGTH - 8));

        // s2 time
        out.write(0);
        out.write(0);
        // s2 random bytes
        out.write(generateRandomBytes(HANDSHAKE_LENGTH - 8));
    }

    public static byte[] readToBuffer(BufferedReader in) throws IOException {
        int readByte;
        int handshakeLength = HANDSHAKE_LENGTH + VERSION_LENGTH;
        byte[] buffer = new byte[handshakeLength];
        int i = 0;
        while ((readByte = in.read()) != -1) {
            buffer[i] = (byte) readByte;
            i++;
        }
        return buffer;
    }

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        randomSeed.nextBytes(bytes);
        return bytes;
    }
}
