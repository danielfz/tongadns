package me.danielf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.time.Duration;
import java.time.LocalDateTime;

public class Main {
    static final int BUF_SIZE = 1024;
    static final Duration TTL = Duration.ofHours(1);

    private static String targetIP = "100.112.155.64";
    private static long lastTime = System.currentTimeMillis();

    static void main() {
        try (var sock = new DatagramSocket(53)) {
            updateIP();

            //sock.bind(new InetSocketAddress());
            var buf = new byte[BUF_SIZE];
            var p = new DatagramPacket(buf, buf.length);
            System.out.println(LocalDateTime.now() + " | Listening at " + sock.getLocalSocketAddress());
            while (true) {
                sock.receive(p);
                buildResponse(p);
                sock.send(p);
                if (isPastTTL()) {
                    updateIP();
                }
            }
        } catch (IOException e) {
            System.err.println(LocalDateTime.now() + " | " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private static void buildResponse(DatagramPacket p) {
        var data = p.getData();

        byte[] buf = new byte[BUF_SIZE];
        int i = 0;
        buf[i++] = data[0];
        buf[i++] = data[1];

        buf[i++] = (byte)0x84;
        buf[i++] = (byte)0x00;

        // qdcount
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x01;

        // ancount
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x01;

        // nscount
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x00;

        // arcount
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x00;

        // copy question - skip name
        StringBuilder host = new StringBuilder();
        int k = 12;
        while (data[k] != 0x00) {
            if (data[k] < 0x40) {
                host.append('?');
            } else {
                host.append(Character.valueOf((char)data[k]));
            }
            buf[i++] = data[k++];
        }
        System.out.println(LocalDateTime.now() + " | Query: " + host);
        buf[i++] = data[k++]; // copy the null
        // copy QTYPE and QCLASS (4 bytes)
        buf[i++] = data[k++];
        buf[i++] = data[k++];
        buf[i++] = data[k++];
        buf[i++] = data[k++];

        // Answer

        // offset 12
        buf[i++] = (byte)0xc0;
        buf[i++] = (byte)0x0c;

        // type = A
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x01;

        // class IN
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x01;

        // TTL
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x10;
        buf[i++] = (byte)0x00; // 60s

        // RDLENGTH = 4B
        buf[i++] = (byte)0x00;
        buf[i++] = (byte)0x04;

        for (String b : targetIP.split("[.]")) {
            buf[i++] = (byte)Integer.parseInt(b);
        }

        p.setData(buf);
        p.setLength(i);
    }

    private static boolean isPastTTL() {
        long deltaMillis = System.currentTimeMillis() - lastTime;
        return deltaMillis > TTL.getSeconds() * 1000;
    }

    private static void updateIP() throws IOException {
        System.out.println(LocalDateTime.now() + " | Updating IP address");
        var process = new ProcessBuilder().command("tailscale", "ip").start();
        InputStream out = process.getInputStream();
        new BufferedReader(new InputStreamReader(out)).lines().findFirst().ifPresent(ip -> {
            targetIP = ip;
            lastTime = System.currentTimeMillis();
        });
        process.close();
    }
}


