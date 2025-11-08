package server;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import server.Models.Student;
import server.Models.Poll;

/**
 * Handles a single student client connection.
 * Runs in a separate thread from the thread pool.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final PollManager pollManager;
    private final MainServer server;
    private BufferedReader in;
    private PrintWriter out;
    private Student student;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, PollManager pollManager, MainServer server) {
        this.socket = socket;
        this.pollManager = pollManager;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // Setup I/O streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("[ClientHandler] New connection from: " + socket.getInetAddress());

            // Main message loop
            String line;
            while (running && (line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            if (running) {
                System.out.println("[ClientHandler] Connection error: " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Handle incoming JSON message from client.
     */
    private void handleMessage(String json) {
        if (json.isEmpty()) return;

        try {
            Map<String, String> msg = JsonUtil.parseObject(json);
            String type = msg.get("type");

            if ("join".equals(type)) {
                handleJoin(msg);
            } else if ("answer".equals(type)) {
                handleAnswer(msg);
            } else if ("PING".equals(json)) {
                out.println("PONG");
            } else {
                System.out.println("[ClientHandler] Unknown message type: " + type);
            }

        } catch (Exception e) {
            System.out.println("[ClientHandler] Error parsing message: " + e.getMessage());
        }
    }

    /**
     * Handle student join request.
     */
    private void handleJoin(Map<String, String> msg) {
        String name = msg.get("name");
        if (name == null || name.trim().isEmpty()) {
            sendError("Name is required");
            return;
        }

        // Create student and register
        String studentId = "student_" + System.currentTimeMillis();
        student = new Student(name.trim(), studentId);
        server.registerStudent(studentId, student, this);

        System.out.println("[ClientHandler] Student joined: " + name);

        // Send welcome message
        String welcome = JsonUtil.buildObject(
                "type", "welcome",
                "message", "Welcome " + name + "!",
                "studentId", studentId
        );
        out.println(welcome);

        // If there's a current poll, send it
        Poll currentPoll = pollManager.getCurrentPoll();
        if (currentPoll != null && currentPoll.active) {
            sendPoll(currentPoll);
        }
    }

    /**
     * Handle student answer submission.
     */
    private void handleAnswer(Map<String, String> msg) {
        if (student == null) {
            sendError("Must join first");
            return;
        }

        String pollId = msg.get("pollId");
        String choice = msg.get("choice");

        if (pollId == null || choice == null) {
            sendError("Missing pollId or choice");
            return;
        }

        // Check if already answered this poll
        if (pollId.equals(student.currentPollAnswered)) {
            sendError("Already answered this poll");
            return;
        }

        // Tally the answer
        boolean success = pollManager.tallyAnswer(pollId, choice.toUpperCase());

        if (success) {
            student.currentPollAnswered = pollId;
            student.updateLastSeen();

            String response = JsonUtil.buildObject(
                    "type", "ack",
                    "message", "Answer received: " + choice.toUpperCase()
            );
            out.println(response);

            System.out.println("[ClientHandler] " + student.name + " answered: " + choice);
        } else {
            sendError("Failed to record answer (poll may be closed)");
        }
    }

    /**
     * Send current poll to this client.
     */
    public void sendPoll(Poll poll) {
        if (out == null || poll == null) return;

        // Reset answered status for new poll
        if (student != null) {
            student.currentPollAnswered = null;
        }

        String json = JsonUtil.buildObject(
                "type", "poll",
                "id", poll.id,
                "question", poll.question,
                "options", poll.options,
                "timeout", poll.timeoutSeconds
        );
        out.println(json);
    }

    /**
     * Send result to this client.
     */
    public void sendResult(Poll poll) {
        if (out == null || poll == null) return;

        int[] counts = new int[poll.options.length];
        for (int i = 0; i < poll.options.length; i++) {
            counts[i] = poll.getCount(i);
        }

        String json = JsonUtil.buildObject(
                "type", "result",
                "id", poll.id,
                "counts", counts,
                "correct", poll.revealed ? poll.getCorrectChoice() : null
        );
        out.println(json);
    }

    /**
     * Send error message to client.
     */
    private void sendError(String message) {
        String json = JsonUtil.buildObject(
                "type", "error",
                "message", message
        );
        out.println(json);
    }

    /**
     * Cleanup resources.
     */
    private void cleanup() {
        running = false;

        if (student != null) {
            server.unregisterStudent(student.id);
            System.out.println("[ClientHandler] Student disconnected: " + student.name);
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("[ClientHandler] Cleanup error: " + e.getMessage());
        }
    }

    /**
     * Gracefully close this handler.
     */
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}