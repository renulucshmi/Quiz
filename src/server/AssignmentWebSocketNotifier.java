package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal WebSocket broadcaster without external deps.
 * Supports text frame broadcast to all connected clients.
 * NOTE: This is a very small subset implementation for notifications only.
 */
public class AssignmentWebSocketNotifier implements Runnable {
    private final int port;
    private volatile boolean running = true;

    private final Set<SocketChannel> clients = Collections.synchronizedSet(new HashSet<>());

    public AssignmentWebSocketNotifier(int port) {
        this.port = port;
    }

    public void stop() { running = false; }

    public void broadcast(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = buildTextFrame(payload);
        synchronized (clients) {
            clients.removeIf(sc -> !sc.isOpen());
            for (SocketChannel sc : clients) {
                try {
                    frame.rewind();
                    sc.write(frame);
                } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public void run() {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            server.configureBlocking(true);
            System.out.println("[AssignmentWS] WebSocket notifier listening on ws://localhost:" + port + "/assignments");
            while (running) {
                SocketChannel sc = server.accept(); // blocking accept
                if (sc == null) continue;
                sc.configureBlocking(true); // we use a dedicated thread per client
                String req = readHttpRequest(sc);
                if (req.contains("Upgrade: websocket") && req.contains("Sec-WebSocket-Key:")) {
                    String key = extractKey(req);
                    String accept = computeWebSocketAccept(key);
                    String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                            + "Upgrade: websocket\r\n"
                            + "Connection: Upgrade\r\n"
                            + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
                    sc.write(ByteBuffer.wrap(resp.getBytes(StandardCharsets.UTF_8)));
                    clients.add(sc);
                    System.out.println("[AssignmentWS] Client connected: " + sc.getRemoteAddress());
                    // start reader thread for ping/pong & close handling
                    Thread t = new Thread(() -> clientLoop(sc), "WSClient-" + sc.hashCode());
                    t.setDaemon(true);
                    t.start();
                } else {
                    safeClose(sc);
                }
            }
        } catch (IOException e) {
            System.err.println("[AssignmentWS] Error: " + e.getMessage());
        }
    }

    private void clientLoop(SocketChannel sc) {
        try {
            while (running && sc.isOpen()) {
                Frame f = readFrame(sc);
                if (f == null) { // closed or error
                    break;
                }
                switch (f.opcode) {
                    case 0x8: // close
                        sendFrame(sc, (byte)0x8, new byte[0]);
                        break;
                    case 0x9: // ping -> pong
                        sendFrame(sc, (byte)0xA, f.payload);
                        break;
                    case 0x1: // text from client (ignore, could log)
                        // Optional: log first 60 chars
                        break;
                    default: // ignore other opcodes
                        break;
                }
                if (f.opcode == 0x8) { // after close frame
                    break;
                }
            }
        } catch (Exception ex) {
            // ignore, client will be removed
        } finally {
            clients.remove(sc);
            safeClose(sc);
        }
    }

    private static class Frame {
        final byte opcode;
        final byte[] payload;
        Frame(byte opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; }
    }

    private Frame readFrame(SocketChannel sc) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(2);
        if (!readFully(sc, header)) return null;
        header.flip();
        byte b0 = header.get();
        byte b1 = header.get();
        byte opcode = (byte) (b0 & 0x0F);
        boolean masked = (b1 & (byte)0x80) != 0;
        long len = (b1 & 0x7F);
        if (len == 126) {
            ByteBuffer ext = ByteBuffer.allocate(2);
            if (!readFully(sc, ext)) return null;
            ext.flip();
            len = Short.toUnsignedInt(ext.getShort());
        } else if (len == 127) {
            ByteBuffer ext = ByteBuffer.allocate(8);
            if (!readFully(sc, ext)) return null;
            ext.flip();
            len = ext.getLong();
        }
        byte[] mask = null;
        if (masked) {
            ByteBuffer mbuf = ByteBuffer.allocate(4);
            if (!readFully(sc, mbuf)) return null;
            mbuf.flip();
            mask = new byte[4];
            mbuf.get(mask);
        }
        byte[] payload = new byte[(int)len];
        if (len > 0) {
            ByteBuffer pbuf = ByteBuffer.allocate((int)len);
            if (!readFully(sc, pbuf)) return null;
            pbuf.flip();
            pbuf.get(payload);
        }
        if (masked && mask != null) {
            for (int i=0;i<payload.length;i++) payload[i] = (byte)(payload[i] ^ mask[i % 4]);
        }
        return new Frame(opcode, payload);
    }

    private boolean readFully(SocketChannel sc, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int r = sc.read(buf);
            if (r == -1) return false;
            if (r == 0) { // avoid busy loop
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            }
        }
        return true;
    }

    private void sendFrame(SocketChannel sc, byte opcode, byte[] payload) throws IOException {
        int payloadLen = payload.length;
        int headerSize = 2;
        if (payloadLen >= 126 && payloadLen <= 65535) headerSize += 2; else if (payloadLen > 65535) headerSize += 8;
        ByteBuffer buf = ByteBuffer.allocate(headerSize + payloadLen);
        buf.put((byte) (0x80 | opcode)); // FIN + opcode
        if (payloadLen < 126) {
            buf.put((byte) payloadLen);
        } else if (payloadLen <= 65535) {
            buf.put((byte)126);
            buf.putShort((short)payloadLen);
        } else {
            buf.put((byte)127);
            buf.putLong(payloadLen);
        }
        buf.put(payload);
        buf.flip();
        sc.write(buf);
    }

    private void safeClose(SocketChannel sc) {
        try { sc.close(); } catch (IOException ignored) {}
    }

    // === Helpers restored after refactor ===
    private static String readHttpRequest(SocketChannel sc) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(2048);
        sc.read(buf);
        buf.flip();
        byte[] b = new byte[buf.remaining()];
        buf.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String extractKey(String req) {
        for (String line : req.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                return line.split(":",2)[1].trim();
            }
        }
        return "";
    }

    private static String computeWebSocketAccept(String key) {
        try {
            String concat = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] sha1 = md.digest(concat.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(sha1);
        } catch (Exception e) {
            return "";
        }
    }

    private static ByteBuffer buildTextFrame(byte[] payload) {
        int payloadLen = payload.length;
        int headerSize = 2;
        if (payloadLen >= 126 && payloadLen <= 65535) headerSize += 2; else if (payloadLen > 65535) headerSize += 8;
        ByteBuffer buf = ByteBuffer.allocate(headerSize + payloadLen);
        byte b0 = (byte) 0x81; // FIN + text frame
        buf.put(b0);
        if (payloadLen < 126) {
            buf.put((byte) payloadLen);
        } else if (payloadLen <= 65535) {
            buf.put((byte) 126);
            buf.putShort((short) payloadLen);
        } else {
            buf.put((byte) 127);
            buf.putLong(payloadLen);
        }
        buf.put(payload);
        buf.flip();
        return buf;
    }
}
