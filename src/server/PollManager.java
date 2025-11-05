package server;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Poll State Manager
 * Member 5: Thread-Safe Data Management
 *
 * Responsibilities:
 * - Manage poll lifecycle (create, start, end, reveal)
 * - Use AtomicReference for current poll
 * - Use ConcurrentHashMap for student answers
 * - Thread-safe vote counting
 * - Prevent duplicate votes
 */
public class PollManager {

    public PollManager() {
        // TODO: Member 5 - Initialize thread-safe structures
    }

    // TODO: Member 5 - Add methods:
    // - createPoll()
    // - startPoll()
    // - endPoll()
    // - revealAnswer()
    // - submitAnswer()
    // - getStatistics()
}
