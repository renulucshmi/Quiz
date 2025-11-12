package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Simple CLI tool to upload an assignment file to the server using the custom TCP protocol.
 * Usage:
 *   java -cp out client.AssignmentUploadClient <host> <port> <assignmentId> <studentName> <filePath>
 * Example:
 *   java -cp out client.AssignmentUploadClient 127.0.0.1 9001 A1 Alice README.md
 */
public class AssignmentUploadClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.out.println("Usage: java -cp out client.AssignmentUploadClient <host> <port> <assignmentId> <studentName> <filePath>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String assignmentId = args[2];
        String studentName = args[3];
        Path file = Path.of(args[4]);
        if (!Files.exists(file)) {
            System.err.println("File not found: " + file);
            return;
        }
        byte[] data = Files.readAllBytes(file);
        String headerJson = String.format("{\"assignmentId\":\"%s\",\"studentName\":\"%s\",\"filename\":\"%s\",\"filesize\":%d}",
                assignmentId, escape(studentName), escape(file.getFileName().toString()), data.length);
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
        ByteBuffer lenBuf = ByteBuffer.allocate(4).putInt(headerBytes.length);
        lenBuf.flip();

        try (SocketChannel sc = SocketChannel.open()) {
            sc.connect(new InetSocketAddress(host, port));
            // send header length
            while (lenBuf.hasRemaining()) sc.write(lenBuf);
            // send header
            ByteBuffer hb = ByteBuffer.wrap(headerBytes);
            while (hb.hasRemaining()) sc.write(hb);
            // send file data
            ByteBuffer fb = ByteBuffer.wrap(data);
            while (fb.hasRemaining()) sc.write(fb);
            System.out.println("Upload sent successfully: " + file.getFileName() + " (" + data.length + " bytes)");
        } catch (IOException e) {
            System.err.println("Upload failed: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

