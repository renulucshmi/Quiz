package server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data models for the polling system.
 * Thread-safe structures for concurrent access.
 */
public class Models {

    /**
     * Represents a poll with question, options, and correct answer.
     */
    public static class Poll {
        public final String id;
        public final String question;
        public final String[] options;
        public final int correctIndex; // 0=A, 1=B, 2=C, 3=D
        public final long startTimeMillis;
        public final int timeoutSeconds;
        public boolean active;
        public boolean revealed;

        // Thread-safe vote counts per option
        public final ConcurrentHashMap<Integer, AtomicInteger> counts;

        public Poll(String id, String question, String[] options, int correctIndex, int timeoutSeconds) {
            this.id = id;
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
            this.timeoutSeconds = timeoutSeconds;
            this.startTimeMillis = System.currentTimeMillis();
            this.active = false;
            this.revealed = false;
            this.counts = new ConcurrentHashMap<>();

            // Initialize counts for each option
            for (int i = 0; i < options.length; i++) {
                counts.put(i, new AtomicInteger(0));
            }
        }

        public void incrementCount(int optionIndex) {
            if (optionIndex >= 0 && optionIndex < options.length) {
                counts.get(optionIndex).incrementAndGet();
            }
        }

        public int getCount(int optionIndex) {
            return counts.getOrDefault(optionIndex, new AtomicInteger(0)).get();
        }

        public int getTotalVotes() {
            return counts.values().stream()
                    .mapToInt(AtomicInteger::get)
                    .sum();
        }

        public String getCorrectChoice() {
            return indexToChoice(correctIndex);
        }

        public static String indexToChoice(int index) {
            return String.valueOf((char) ('A' + index));
        }

        public static int choiceToIndex(String choice) {
            if (choice == null || choice.isEmpty())
                return -1;
            char c = choice.toUpperCase().charAt(0);
            return c - 'A';
        }
    }

    /**
     * Represents a connected student client.
     */
    public static class Student {
        public final String name;
        public final String id; // Unique identifier
        public long lastSeenMillis;

        // Track if student has answered current poll
        public String currentPollAnswered = null;

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
     * Statistics snapshot for dashboard.
     */
    public static class PollStats {
        public boolean active;
        public String pollId;
        public String question;
        public String[] options;
        public int[] counts;
        public double[] percentages;
        public String correctChoice;
        public boolean revealed;
        public int totalVotes;

        public PollStats() {
            this.active = false;
        }

        public PollStats(Poll poll) {
            this.active = poll.active;
            this.pollId = poll.id;
            this.question = poll.question;
            this.options = poll.options;
            this.revealed = poll.revealed;

            int optCount = poll.options.length;
            this.counts = new int[optCount];
            this.percentages = new double[optCount];

            int total = poll.getTotalVotes();
            this.totalVotes = total;

            for (int i = 0; i < optCount; i++) {
                counts[i] = poll.getCount(i);
                percentages[i] = total > 0 ? (counts[i] * 100.0 / total) : 0.0;
            }

            this.correctChoice = poll.revealed ? poll.getCorrectChoice() : null;
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
