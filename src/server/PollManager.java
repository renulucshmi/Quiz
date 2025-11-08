package server;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import server.Models.Poll;
import server.Models.PollStats;

/**
 * Thread-safe poll management.
 * Handles poll lifecycle: create, start, end, reveal, and vote tallying.
 */
public class PollManager {
    
    // Current active poll (thread-safe reference)
    private final AtomicReference<Poll> currentPoll = new AtomicReference<>(null);
    
    // Poll ID counter
    private final AtomicInteger pollIdCounter = new AtomicInteger(1);
    
    /**
     * Create a new poll (but don't start it yet).
     */
    public Poll createPoll(String question, String[] options, int correctIndex, int timeoutSeconds) {
        String pollId = "poll" + pollIdCounter.getAndIncrement();
        Poll poll = new Poll(pollId, question, options, correctIndex, timeoutSeconds);
        currentPoll.set(poll);
        System.out.println("[PollManager] Poll created: " + pollId + " - " + question);
        return poll;
    }
    
    /**
     * Start the current poll (make it active).
     */
    public Poll startPoll() {
        Poll poll = currentPoll.get();
        if (poll == null) {
            System.out.println("[PollManager] No poll to start. Create one first.");
            return null;
        }
        if (poll.active) {
            System.out.println("[PollManager] Poll already active.");
            return poll;
        }
        poll.active = true;
        System.out.println("[PollManager] Poll started: " + poll.id);
        return poll;
    }
    
    /**
     * End the current poll (stop accepting answers).
     */
    public Poll endPoll() {
        Poll poll = currentPoll.get();
        if (poll == null) {
            System.out.println("[PollManager] No active poll to end.");
            return null;
        }
        poll.active = false;
        System.out.println("[PollManager] Poll ended: " + poll.id);
        return poll;
    }
    
    /**
     * Reveal the correct answer.
     */
    public Poll revealAnswer() {
        Poll poll = currentPoll.get();
        if (poll == null) {
            System.out.println("[PollManager] No poll to reveal.");
            return null;
        }
        poll.revealed = true;
        System.out.println("[PollManager] Answer revealed: " + poll.getCorrectChoice());
        return poll;
    }
    
    /**
     * Record a student's answer.
     * Returns true if answer was recorded, false if poll not active or invalid.
     */
    public boolean tallyAnswer(String pollId, String choice) {
        Poll poll = currentPoll.get();
        
        // Validate poll exists and is active
        if (poll == null || !poll.id.equals(pollId)) {
            return false;
        }
        if (!poll.active) {
            System.out.println("[PollManager] Poll not active, answer rejected.");
            return false;
        }
        
        // Convert choice (A/B/C/D) to index
        int index = Poll.choiceToIndex(choice);
        if (index < 0 || index >= poll.options.length) {
            System.out.println("[PollManager] Invalid choice: " + choice);
            return false;
        }
        
        // Increment count (thread-safe)
        poll.incrementCount(index);
        System.out.println("[PollManager] Answer recorded: " + choice + " for " + pollId);
        return true;
    }
    
    /**
     * Get current poll (may be null).
     */
    public Poll getCurrentPoll() {
        return currentPoll.get();
    }
    
    /**
     * Get a snapshot of current statistics for the dashboard.
     */
    public PollStats getStats() {
        Poll poll = currentPoll.get();
        if (poll == null) {
            return new PollStats(); // Empty stats
        }
        return new PollStats(poll);
    }
    
    /**
     * Check if there's an active poll.
     */
    public boolean hasActivePoll() {
        Poll poll = currentPoll.get();
        return poll != null && poll.active;
    }
}
