package server;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an assignment created by the instructor.
 */
public class Assignment {
    private static final AtomicInteger ID_GEN = new AtomicInteger(1);

    public final String id;        // e.g. A1
    public final String title;
    public final String description;
    public final Instant createdAt;
    public final Instant dueAt;    // optional (can be null)

    public Assignment(String title, String description, Instant dueAt) {
        this.id = "A" + ID_GEN.getAndIncrement();
        this.title = title;
        this.description = description == null ? "" : description;
        this.createdAt = Instant.now();
        this.dueAt = dueAt;
    }
}

