package server;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class VoteManager {
    private static VoteManager instance;
    private final Map<String, Vote> votes = new ConcurrentHashMap<>();
    private final AtomicInteger voteIdCounter = new AtomicInteger(1);

    private VoteManager() {}

    public static synchronized VoteManager getInstance() {
        if (instance == null) {
            instance = new VoteManager();
        }
        return instance;
    }

    public synchronized String createVote(String question, String[] options, boolean allowRevote, String deadline) {
        if (options == null || options.length != 4) {
            throw new IllegalArgumentException("Exactly 4 options required");
        }

        // Check for duplicate options
        Set<String> uniqueOptions = new HashSet<>(Arrays.asList(options));
        if (uniqueOptions.size() != 4) {
            throw new IllegalArgumentException("All options must be unique (no duplicates)");
        }

        // Validate that all options have content
        for (String opt : options) {
            if (opt == null || opt.trim().isEmpty()) {
                throw new IllegalArgumentException("All options must have values");
            }
        }

        String voteId = "v" + voteIdCounter.getAndIncrement();
        Vote vote = new Vote(voteId, question, options, allowRevote, deadline);
        votes.put(voteId, vote);
        return voteId;
    }

    public boolean openVote(String voteId) {
        Vote vote = votes.get(voteId);
        if (vote == null) return false;
        vote.setState(VoteState.OPEN);
        return true;
    }

    public boolean closeVote(String voteId) {
        Vote vote = votes.get(voteId);
        if (vote == null) return false;
        vote.setState(VoteState.CLOSED);
        return true;
    }

    public boolean castVote(String voteId, String studentName, int choiceIndex) {
        Vote vote = votes.get(voteId);
        if (vote == null) return false;
        
        if (vote.getState() != VoteState.OPEN) {
            return false; // Voting not open
        }

        // Deadline checking removed - deadline is just informational text now

        if (choiceIndex < 0 || choiceIndex > 3) {
            return false; // Invalid choice
        }

        return vote.recordVote(studentName, choiceIndex);
    }

    public Map<String, Object> getStatusSnapshot(String voteId) {
        Vote vote = votes.get(voteId);
        if (vote == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Vote not found");
            return error;
        }

        Map<String, Object> status = new HashMap<>();
        status.put("success", true);
        status.put("state", vote.getState().toString());
        status.put("question", vote.getQuestion());
        status.put("options", vote.getOptions());
        
        int[] counts = vote.getCounts();
        status.put("counts", counts);
        
        double[] percent = vote.getPercentages();
        status.put("percent", percent);
        
        if (vote.getState() == VoteState.CLOSED) {
            status.put("winnerIndexes", vote.getWinnerIndexes());
        }
        
        return status;
    }

    public Vote getVote(String voteId) {
        return votes.get(voteId);
    }

    public Collection<Vote> getAllVotes() {
        return votes.values();
    }

    // Inner class representing a vote
    public static class Vote {
        private final String id;
        private final String question;
        private final String[] options;
        private final boolean allowRevote;
        private final String deadline; // Optional deadline text (e.g., "15th December, 5:00 PM")
        private VoteState state;
        private final AtomicIntegerArray counts; // 4 counters
        private final Map<String, Integer> votesByStudent; // studentName -> choiceIndex

        public Vote(String id, String question, String[] options, boolean allowRevote, String deadline) {
            this.id = id;
            this.question = question;
            this.options = options;
            this.allowRevote = allowRevote;
            this.deadline = deadline;
            this.state = VoteState.CREATED;
            this.counts = new AtomicIntegerArray(4);
            this.votesByStudent = new ConcurrentHashMap<>();
        }

        public synchronized boolean recordVote(String studentName, int choiceIndex) {
            Integer previousChoice = votesByStudent.get(studentName);

            if (previousChoice != null) {
                if (!allowRevote) {
                    return false; // Revote not allowed
                }
                // Decrement previous choice
                counts.decrementAndGet(previousChoice);
            }

            // Record new vote
            votesByStudent.put(studentName, choiceIndex);
            counts.incrementAndGet(choiceIndex);
            return true;
        }

        public int[] getCounts() {
            int[] result = new int[4];
            for (int i = 0; i < 4; i++) {
                result[i] = counts.get(i);
            }
            return result;
        }

        public double[] getPercentages() {
            int[] countsArray = getCounts();
            int total = 0;
            for (int count : countsArray) {
                total += count;
            }

            double[] percent = new double[4];
            if (total == 0) {
                return percent; // All zeros
            }

            for (int i = 0; i < 4; i++) {
                percent[i] = (countsArray[i] * 100.0) / total;
            }
            return percent;
        }

        public List<Integer> getWinnerIndexes() {
            int[] countsArray = getCounts();
            int maxCount = 0;
            for (int count : countsArray) {
                if (count > maxCount) {
                    maxCount = count;
                }
            }

            List<Integer> winners = new ArrayList<>();
            if (maxCount > 0) {
                for (int i = 0; i < 4; i++) {
                    if (countsArray[i] == maxCount) {
                        winners.add(i);
                    }
                }
            }
            return winners;
        }

        public String getId() { return id; }
        public String getQuestion() { return question; }
        public String[] getOptions() { return options; }
        public boolean isAllowRevote() { return allowRevote; }
        public String getDeadline() { return deadline; }
        public VoteState getState() { return state; }
        public void setState(VoteState state) { this.state = state; }
    }

    public enum VoteState {
        CREATED, OPEN, CLOSED
    }
}
