package server;

/**
 * Data models for the polling system.
 * Thread-safe structures for concurrent access.
 */
public class Models {

    /**
     * Represents a connected student client.
     */
    public static class Student {
        public final String name;
        public final String id; // Unique identifier
        public long lastSeenMillis;

        public Student(String name, String id) {
            this.name = name;
            this.id = id;
            this.lastSeenMillis = System.currentTimeMillis();
        }

        public void updateLastSeen() {
            this.lastSeenMillis = System.currentTimeMillis();
        }
    }

    /**
     * Represents a chat message in the discussion.
     */
    public static class ChatMessage {
        public final int id;
        public final String username;
        public final String message;
        public final long timestamp;

        public ChatMessage(int id, String username, String message) {
            this.id = id;
            this.username = username;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public String getFormattedTime() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            return sdf.format(new java.util.Date(timestamp));
        }
    }
}
