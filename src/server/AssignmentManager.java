package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages assignments and uploaded student files using NIO.
 */
public class AssignmentManager {
    private final Map<String, Assignment> assignments = new ConcurrentHashMap<>();
    private final Map<String, List<UploadRecord>> uploads = new ConcurrentHashMap<>();
    private final Path storageRoot;

    public static class UploadRecord {
        public final String assignmentId;
        public final String studentName;
        public final String originalFilename;
        public final Path storedPath;
        public final long size;
        public final Instant uploadedAt;

        public UploadRecord(String assignmentId, String studentName, String originalFilename, Path storedPath, long size) {
            this.assignmentId = assignmentId;
            this.studentName = studentName;
            this.originalFilename = originalFilename;
            this.storedPath = storedPath;
            this.size = size;
            this.uploadedAt = Instant.now();
        }
    }

    public AssignmentManager(Path root) throws IOException {
        this.storageRoot = root;
        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
    }

    public Assignment createAssignment(String title, String description, Instant dueAt) {
        Assignment a = new Assignment(title, description, dueAt);
        assignments.put(a.id, a);
        uploads.put(a.id, Collections.synchronizedList(new ArrayList<>()));
        return a;
    }

    public Collection<Assignment> listAssignments() {
        return assignments.values();
    }

    public Assignment get(String id) { return assignments.get(id); }

    public UploadRecord recordUpload(String assignmentId, String studentName, String filename, byte[] data) throws IOException {
        Assignment a = assignments.get(assignmentId);
        if (a == null) throw new IllegalArgumentException("Unknown assignment: " + assignmentId);

        Path dir = storageRoot.resolve(assignmentId);
        Files.createDirectories(dir);

        // Basic filename sanitization
        String safe = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String outName = studentName + "_" + System.currentTimeMillis() + "_" + safe;
        Path out = dir.resolve(outName);

        try (FileChannel fc = FileChannel.open(out, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining()) fc.write(buf);
            fc.force(true);
        }

        UploadRecord r = new UploadRecord(assignmentId, studentName, filename, out, data.length);
        uploads.get(assignmentId).add(r);
        return r;
    }

    public List<UploadRecord> getUploads(String assignmentId) {
        return uploads.getOrDefault(assignmentId, Collections.emptyList());
    }
}

