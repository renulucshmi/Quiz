package client;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

/**
 * Student client for connecting to the polling server.
 *
 * Features:
 * - Connects to TCP server
 * - Receives poll questions
 * - Submits answers
 * - Displays results
 */
public class StudentClient {

    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 8088;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String studentName;
    private volatile boolean running = true;

    public StudentClient(String name) {
        this.studentName = name;
    }

    /**
     * Connect to the server and start communication.
     */
    public void connect() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   Remote Classroom Polling System - Student Client       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("[Client] Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);
        System.out.println();

        // Send join message
        sendJoin();

        // Start listener thread for server messages
        Thread listenerThread = new Thread(this::listenForMessages, "ServerListener");
        listenerThread.start();

        // Wait for listener to finish
        try {
            listenerThread.join();
        } catch (InterruptedException e) {
            System.out.println("[Client] Interrupted");
        }
    }

    /**
     * Send join message to server.
     */
    private void sendJoin() {
        String json = buildJson("type", "join", "name", studentName);
        out.println(json);
        System.out.println("[Client] Joining as: " + studentName);
    }

    /**
     * Listen for messages from server.
     */
    private void listenForMessages() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                handleMessage(line.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Client] Connection lost: " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Handle incoming message from server.
     */
    private void handleMessage(String json) {
        if (json.isEmpty())
            return;

        if ("PONG".equals(json)) {
            return; // Heartbeat response
        }

        try {
            Map<String, String> msg = parseJson(json);
            String type = msg.get("type");

            switch (type) {
                case "welcome":
                    handleWelcome(msg);
                    break;
                case "poll":
                    handlePoll(msg, json);
                    break;
                case "result":
                    handleResult(msg);
                    break;
                case "ack":
                    handleAck(msg);
                    break;
                case "chat":
                    handleChatMessage(msg);
                    break;
                case "chatCleared":
                    handleChatCleared();
                    break;
                case "error":
                    handleError(msg);
                    break;
                default:
                    System.out.println("[Client] Unknown message type: " + type);
            }

        } catch (Exception e) {
            System.err.println("[Client] Error handling message: " + e.getMessage());
        }
    }

    /**
     * Handle welcome message.
     */
    private void handleWelcome(Map<String, String> msg) {
        String message = msg.get("message");
        System.out.println("✓ " + message);
        System.out.println("[Client] Waiting for poll...");
        System.out.println();
    }

    /**
     * Handle poll question.
     */
    private void handlePoll(Map<String, String> msg, String rawJson) {
        String pollId = msg.get("id");
        String question = msg.get("question");

        System.out.println("\n" + "═".repeat(60));
        System.out.println("📊 NEW POLL RECEIVED");
        System.out.println("═".repeat(60));
        System.out.println("Question: " + question);
        System.out.println();

        // Extract options (hacky but works for simple JSON)
        String[] options = extractOptions(rawJson);
        if (options != null) {
            for (String opt : options) {
                System.out.println("  " + opt);
            }
        }

        System.out.println("═".repeat(60));

        // Prompt for answer
        promptForAnswer(pollId);
    }

    /**
     * Extract options array from JSON (simple approach).
     */
    private String[] extractOptions(String json) {
        try {
            int start = json.indexOf("\"options\":[") + 11;
            int end = json.indexOf("]", start);
            String optionsStr = json.substring(start, end);

            // Split by comma and clean up
            String[] parts = optionsStr.split("\",\"");
            String[] options = new String[parts.length];

            for (int i = 0; i < parts.length; i++) {
                options[i] = parts[i].replace("\"", "").replace("[", "").replace("]", "");
            }

            return options;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Prompt user to enter answer.
     */
    private void promptForAnswer(String pollId) {
        System.out.print("\nYour answer (A/B/C/D): ");

        // Read answer in a separate thread to not block listener
        Thread inputThread = new Thread(() -> {
            try {
                Scanner scanner = new Scanner(System.in);
                String answer = scanner.nextLine().trim().toUpperCase();

                // Validate input
                if (!answer.matches("[A-D]")) {
                    System.out.println("✗ Invalid choice. Must be A, B, C, or D.");
                    return;
                }

                // Send answer
                sendAnswer(pollId, answer);

            } catch (Exception e) {
                System.err.println("[Client] Input error: " + e.getMessage());
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }

    /**
     * Send answer to server.
     */
    private void sendAnswer(String pollId, String choice) {
        String json = buildJson("type", "answer", "pollId", pollId, "choice", choice);
        out.println(json);
        System.out.println("[Client] Submitted answer: " + choice);
    }

    /**
     * Handle acknowledgment.
     */
    private void handleAck(Map<String, String> msg) {
        String message = msg.get("message");
        System.out.println("✓ " + message);
        System.out.println("[Client] Waiting for results or next poll...");
    }

    /**
     * Handle result message.
     */
    private void handleResult(Map<String, String> msg) {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📈 POLL RESULTS");
        System.out.println("═".repeat(60));

        String correct = msg.get("correct");
        if (correct != null && !correct.equals("null")) {
            System.out.println("Correct Answer: " + correct);
        }

        // Note: counts are in array format, would need better parsing for full display
        System.out.println("\nCheck the dashboard for detailed results:");
        System.out.println("http://localhost:8090/");
        System.out.println("═".repeat(60));
        System.out.println();
    }

    /**
     * Handle error message.
     */
    private void handleError(Map<String, String> msg) {
        String message = msg.get("message");
        System.err.println("✗ Error: " + message);
    }

    /**
     * Handle incoming chat message.
     */
    private void handleChatMessage(Map<String, String> msg) {
        String username = msg.get("username");
        String message = msg.get("message");

        if ("SYSTEM".equals(username)) {
            System.out.println("\n[SYSTEM] " + message);
        } else {
            System.out.println("\n💬 " + username + ": " + message);
        }
    }

    /**
     * Handle chat cleared notification.
     */
    private void handleChatCleared() {
        System.out.println("\n[CHAT] Chat history has been cleared by instructor");
    }

    /**
     * Simple JSON parser (basic key-value pairs only).
     */
    private Map<String, String> parseJson(String json) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();

        json = json.trim();
        if (json.startsWith("{"))
            json = json.substring(1);
        if (json.endsWith("}"))
            json = json.substring(0, json.length() - 1);

        // Simple split (doesn't handle nested objects or arrays properly)
        String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(":");
            if (colonIdx > 0) {
                String key = unquote(pair.substring(0, colonIdx).trim());
                String value = unquote(pair.substring(colonIdx + 1).trim());
                map.put(key, value);
            }
        }

        return map;
    }

    /**
     * Build simple JSON object.
     */
    private String buildJson(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0)
                sb.append(",");
            sb.append("\"").append(keyValues[i]).append("\":\"");
            sb.append(keyValues[i + 1]).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Remove quotes from string.
     */
    private String unquote(String s) {
        if (s.startsWith("\""))
            s = s.substring(1);
        if (s.endsWith("\""))
            s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * Cleanup resources.
     */
    private void cleanup() {
        running = false;
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            // Ignore
        }
        System.out.println("\n[Client] Disconnected from server");
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        String name = null;

        // Check for --name argument
        if (args.length >= 2 && args[0].equals("--name")) {
            name = args[1];
        } else if (args.length >= 1) {
            name = args[0];
        }

        // Prompt for name if not provided
        if (name == null || name.trim().isEmpty()) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your name: ");
            name = scanner.nextLine().trim();
        }

        if (name.isEmpty()) {
            System.err.println("Name cannot be empty");
            System.exit(1);
        }

        StudentClient client = new StudentClient(name);

        try {
            client.connect();
        } catch (IOException e) {
            System.err.println("Failed to connect: " + e.getMessage());
            System.err.println("\nMake sure the server is running on " + SERVER_HOST + ":" + SERVER_PORT);
            System.exit(1);
        }
    }
}