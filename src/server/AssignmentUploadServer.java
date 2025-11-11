package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;

/**
 * NIO TCP server for assignment file uploads.
 * Protocol:
 *  4-byte big-endian int: header length N
 *  N bytes UTF-8 header JSON: {"assignmentId":"A1","studentName":"Alice","filename":"work.txt","filesize":123}
 *  filesize bytes: raw file data
 */
public class AssignmentUploadServer implements Runnable {
    private final int port;
    private final AssignmentManager manager;
    private final AssignmentWebSocketNotifier notifier;
    private volatile boolean running = true;

    public AssignmentUploadServer(int port, AssignmentManager manager, AssignmentWebSocketNotifier notifier) {
        this.port = port;
        this.manager = manager;
        this.notifier = notifier;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        try (Selector selector = Selector.open(); ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("[AssignmentUploadServer] Listening on port " + port);

            while (running) {
                selector.select(500);
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) {
                        SocketChannel sc = server.accept();
                        if (sc == null) continue;
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ, new ConnState());
                    } else if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ConnState state = (ConnState) key.attachment();
                        try {
                            if (!state.read(sc)) {
                                key.cancel();
                                sc.close();
                            } else if (state.complete()) {
                                handleUpload(state);
                                key.cancel();
                                sc.close();
                            }
                        } catch (IOException ex) {
                            key.cancel();
                            sc.close();
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[AssignmentUploadServer] Error: " + e.getMessage());
        }
    }

    private void handleUpload(ConnState state) {
        try {
            String header = new String(state.headerBytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            java.util.Map<String, String> map = JsonUtil.parseObject(header);
            String assignmentId = map.get("assignmentId");
            String studentName = map.get("studentName");
            String filename = map.get("filename");
            String filesizeStr = map.get("filesize");
            long declared = Long.parseLong(filesizeStr);
            if (declared != state.fileBytes.size()) {
                System.err.println("[AssignmentUploadServer] Size mismatch declared=" + declared + " read=" + state.fileBytes.size());
            }
            byte[] fileData = state.fileBytes.toByteArray();
            AssignmentManager.UploadRecord rec = manager.recordUpload(assignmentId, studentName, filename, fileData);
            System.out.println("[AssignmentUploadServer] Upload received: " + filename + " from " + studentName + " (" + fileData.length + " bytes)");

            if (notifier != null) {
                notifier.broadcast(JsonUtil.buildObject(
                        "type", "assignment_upload",
                        "assignmentId", rec.assignmentId,
                        "studentName", rec.studentName,
                        "filename", rec.originalFilename,
                        "size", rec.size,
                        "storedPath", rec.storedPath.toString(),
                        "uploadedAt", rec.uploadedAt.toString()
                ));
            }
        } catch (Exception e) {
            System.err.println("[AssignmentUploadServer] handleUpload error: " + e.getMessage());
        }
    }

    private static class ConnState {
        private enum Phase { HEADER_LEN, HEADER, FILE }
        private Phase phase = Phase.HEADER_LEN;
        private final ByteBuffer intBuf = ByteBuffer.allocate(4);
        private int headerLen = -1;
        private final ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        private long fileExpected = -1;
        private final ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();

        boolean read(SocketChannel sc) throws IOException {
            if (phase == Phase.HEADER_LEN) {
                int r = sc.read(intBuf);
                if (r == -1) return false;
                if (!intBuf.hasRemaining()) {
                    intBuf.flip();
                    headerLen = intBuf.getInt();
                    intBuf.clear();
                    if (headerLen <= 0) return false;
                    phase = Phase.HEADER;
                }
            }
            if (phase == Phase.HEADER) {
                int remaining = headerLen - headerBytes.size();
                ByteBuffer buf = ByteBuffer.allocate(Math.min(8192, remaining));
                int r = sc.read(buf);
                if (r == -1) return false;
                if (r > 0) {
                    buf.flip();
                    byte[] b = new byte[buf.remaining()];
                    buf.get(b);
                    headerBytes.write(b);
                }
                if (headerBytes.size() >= headerLen) {
                    String hdr = new String(headerBytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.Map<String, String> map = JsonUtil.parseObject(hdr);
                    String fs = map.get("filesize");
                    if (fs == null) return false;
                    fileExpected = Long.parseLong(fs);
                    phase = Phase.FILE;
                }
            }
            if (phase == Phase.FILE) {
                ByteBuffer buf = ByteBuffer.allocate(8192);
                int r = sc.read(buf);
                if (r == -1) return false;
                if (r > 0) {
                    buf.flip();
                    byte[] b = new byte[buf.remaining()];
                    buf.get(b);
                    fileBytes.write(b);
                }
                if (fileExpected >= 0 && fileBytes.size() >= fileExpected) {
                    return true; // complete
                }
            }
            return true; // continue
        }

        boolean complete() {
            return phase == Phase.FILE && fileExpected >= 0 && fileBytes.size() >= fileExpected;
        }
    }
}

