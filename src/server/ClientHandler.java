package server;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import server.Models.ChatMessage;
import server.Models.Student;

/**
 * Handles a single student client connection.
 * Runs in a separate thread from the thread pool.
 */
public class ClientHandler implements Runnable, ChatManager.ChatListener {

    private final Socket socket;
    private final ChatManager chatManager;
    private final MainServer server;
    private BufferedReader in;
    private PrintWriter out;
    private Student student;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ChatManager chatManager, MainServer server) {
        this.socket = socket;
        this.chatManager = chatManager;
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
        if (json.isEmpty())
            return;

        try {
            Map<String, String> msg = JsonUtil.parseObject(json);
            String type = msg.get("type");

            if ("join".equals(type)) {
                handleJoin(msg);
            } else if ("chat".equals(type)) {
                handleChat(msg);
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

        // Register as chat listener
        chatManager.addListener(this);

        System.out.println("[ClientHandler] Student joined: " + name);

        // Send welcome message
        String welcome = JsonUtil.buildObject(
                "type", "welcome",
                "message", "Welcome " + name + "!",
                "studentId", studentId);
        out.println(welcome);
    }

    /**
     * Handle chat message from student.
     */
    private void handleChat(Map<String, String> msg) {
        if (student == null) {
            sendError("Must join first");
            return;
        }

        String message = msg.get("message");
        if (message == null || message.trim().isEmpty()) {
            sendError("Empty message");
            return;
        }

        // Post message to chat manager
        ChatMessage chatMessage = chatManager.postMessage(student.name, message);

        if (chatMessage == null) {
            sendError("Chat is currently disabled");
        }
        // No need to send ACK, the broadcast will handle it
    }

    /**
     * Send error message to client.
     */
    private void sendError(String message) {
        String json = JsonUtil.buildObject(
                "type", "error",
                "message", message);
        out.println(json);
    }

    /**
     * ChatListener implementation - called when new chat message arrives.
     */
    @Override
    public void onNewMessage(ChatMessage message) {
        if (out != null) {
            String json = JsonUtil.buildObject(
                    "type", "chat",
                    "id", message.id,
                    "username", message.username,
                    "message", message.message,
                    "timestamp", message.timestamp);
            out.println(json);
        }
    }

    /**
     * ChatListener implementation - called when chat is cleared.
     */
    @Override
    public void onChatCleared() {
        if (out != null) {
            String json = JsonUtil.buildObject(
                    "type", "chatCleared",
                    "message", "Chat history has been cleared");
            out.println(json);
        }
    }

    /**
     * Send vote notification to client.
     */
    public void sendVote(String voteJson) {
        if (out != null) {
            out.println(voteJson);
        }
    }

    /**
     * Send vote closed notification to client.
     */
    public void sendVoteClosed(String voteClosedJson) {
        if (out != null) {
            out.println(voteClosedJson);
        }
    }

    /**
     * Send quiz reveal notification to client.
     */
    public void sendQuizReveal(String quizRevealJson) {
        if (out != null) {
            out.println(quizRevealJson);
        }
    }

    /**
     * Cleanup resources.
     */
    private void cleanup() {
        running = false;

        if (student != null) {
            // Unregister from chat
            chatManager.removeListener(this);

            server.unregisterStudent(student.id);
            System.out.println("[ClientHandler] Student disconnected: " + student.name);
        }

        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null)
                socket.close();
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