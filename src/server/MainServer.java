package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.*;
import server.Models.Poll;
import server.Models.Student;

/**
 * Main TCP server for the Remote Classroom Polling System.
 * 
 * Features:
 * - Multithreaded TCP server using ExecutorService
 * - Instructor CLI for poll management
 * - Embedded HTTP server for web dashboard
 * - Thread-safe student registry and poll state
 */
public class MainServer {

    private static final int TCP_PORT = 8088;
    private static final int HTTP_PORT = 8090;
    private static final int THREAD_POOL_SIZE = 16;

    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final PollManager pollManager;
    private final ChatManager chatManager;
    private final QAManager qaManager;
    private final HttpDashboard dashboard;
    private AssignmentUploadServer assignmentUploadServer;
    private Thread assignmentUploadThread;

    // Thread-safe registry of connected students
    private final ConcurrentHashMap<String, Student> students = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> handlers = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    public MainServer() throws IOException {
        this.serverSocket = new ServerSocket(TCP_PORT);
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.pollManager = new PollManager();
        this.chatManager = new ChatManager();
        this.qaManager = new QAManager();
        this.dashboard = new HttpDashboard(HTTP_PORT, pollManager, chatManager, qaManager, students);
        // create upload server using same assignment manager embedded in dashboard
        // We access private field via reflection since assignmentManager is private (simple workaround without API change)
        try {
            java.lang.reflect.Field f = HttpDashboard.class.getDeclaredField("assignmentManager");
            f.setAccessible(true);
            AssignmentManager am = (AssignmentManager) f.get(dashboard);
            java.lang.reflect.Field n = HttpDashboard.class.getDeclaredField("assignmentNotifier");
            n.setAccessible(true);
            AssignmentWebSocketNotifier notifier = (AssignmentWebSocketNotifier) n.get(dashboard);
            this.assignmentUploadServer = new AssignmentUploadServer(9001, am, notifier);
            this.assignmentUploadThread = new Thread(assignmentUploadServer, "AssignmentUploadServer");
        } catch (Exception e) {
            System.err.println("[MainServer] Failed to init assignment upload server: " + e.getMessage());
        }

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║   Remote Classroom Polling System - Server Started       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("[MainServer] TCP server listening on port " + TCP_PORT);
    }

    /**
     * Start accepting client connections.
     */
    public void start() {
        // Start HTTP dashboard
        dashboard.start();
        if (assignmentUploadThread != null) {
            assignmentUploadThread.start();
            System.out.println("[MainServer] Assignment upload TCP server started on port 9001");
            System.out.println("[MainServer] Assignment WS notifier on port 8081 (path /assignments)");
        }

        // Start CLI thread for instructor
        Thread cliThread = new Thread(this::runCLI, "InstructorCLI");
        cliThread.setDaemon(true);
        cliThread.start();

        // Main accept loop
        System.out.println("[MainServer] Ready to accept student connections...");
        System.out.println();
        printHelp();

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, pollManager, chatManager, this);
                threadPool.submit(handler);

            } catch (IOException e) {
                if (running) {
                    System.err.println("[MainServer] Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Instructor command-line interface.
     */
    private void runCLI() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            try {
                System.out.print("\nInstructor> ");
                String line = scanner.nextLine().trim();

                if (line.isEmpty())
                    continue;

                handleCommand(line);

            } catch (Exception e) {
                System.err.println("[CLI] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Handle instructor commands.
     */
    private void handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "newpoll":
                if (parts.length < 2) {
                    System.out.println("Usage: newpoll <question> | <opt1>;<opt2>;<opt3>;<opt4> | <correct>");
                    System.out.println("Example: newpoll What is 2+2? | 1;4;5;8 | B");
                    return;
                }
                handleNewPoll(parts[1]);
                break;

            case "startpoll":
                handleStartPoll();
                break;

            case "endpoll":
                handleEndPoll();
                break;

            case "reveal":
                handleReveal();
                break;

            case "enablechat":
                handleEnableChat();
                break;

            case "disablechat":
                handleDisableChat();
                break;

            case "clearchat":
                handleClearChat();
                break;

            case "status":
                handleStatus();
                break;

            case "help":
                printHelp();
                break;

            case "exit":
                handleExit();
                break;

            default:
                System.out.println("Unknown command: " + cmd + ". Type 'help' for available commands.");
        }
    }

    /**
     * Create a new poll.
     * Format: newpoll Question? | A;B;C;D | B
     */
    private void handleNewPoll(String args) {
        String[] parts = args.split("\\|");
        if (parts.length != 3) {
            System.out.println("Invalid format. Use: question | options | correct");
            return;
        }

        String question = parts[0].trim();
        String[] optionsRaw = parts[1].trim().split(";");
        String correct = parts[2].trim().toUpperCase();

        if (optionsRaw.length != 4) {
            System.out.println("Must provide exactly 4 options separated by semicolon");
            return;
        }

        // Format options as A) B) C) D)
        String[] options = new String[4];
        for (int i = 0; i < 4; i++) {
            char letter = (char) ('A' + i);
            options[i] = letter + ") " + optionsRaw[i].trim();
        }

        int correctIndex = Poll.choiceToIndex(correct);
        if (correctIndex < 0 || correctIndex > 3) {
            System.out.println("Correct answer must be A, B, C, or D");
            return;
        }

        Poll poll = pollManager.createPoll(question, options, correctIndex, 60);
        System.out.println("✓ Poll created successfully!");
        System.out.println("  ID: " + poll.id);
        System.out.println("  Question: " + question);
        System.out.println("  Correct: " + correct);
        System.out.println("\nUse 'startpoll' to activate it.");
    }

    /**
     * Start the current poll and broadcast to all clients.
     */
    private void handleStartPoll() {
        Poll poll = pollManager.startPoll();
        if (poll == null) {
            System.out.println("✗ No poll to start. Create one with 'newpoll' first.");
            return;
        }

        System.out.println("✓ Poll started and broadcasting to " + handlers.size() + " students...");

        // Broadcast to all connected clients
        for (ClientHandler handler : handlers.values()) {
            handler.sendPoll(poll);
        }

        System.out.println("✓ Poll broadcasted successfully!");
    }

    /**
     * End the current poll.
     */
    private void handleEndPoll() {
        Poll poll = pollManager.endPoll();
        if (poll == null) {
            System.out.println("✗ No poll to end.");
            return;
        }

        System.out.println("✓ Poll ended. Total votes: " + poll.getTotalVotes());

        // Optionally broadcast results
        for (ClientHandler handler : handlers.values()) {
            handler.sendResult(poll);
        }
    }

    /**
     * Reveal the correct answer.
     */
    private void handleReveal() {
        Poll poll = pollManager.revealAnswer();
        if (poll == null) {
            System.out.println("✗ No poll to reveal.");
            return;
        }

        System.out.println("✓ Correct answer revealed: " + poll.getCorrectChoice());
        System.out.println("  Check the dashboard for updated results.");
    }

    /**
     * Enable chat for discussion.
     */
    private void handleEnableChat() {
        chatManager.enableChat();
        System.out.println("✓ Chat enabled for discussion.");
    }

    /**
     * Disable chat.
     */
    private void handleDisableChat() {
        chatManager.disableChat();
        System.out.println("✓ Chat disabled.");
    }

    /**
     * Clear all chat messages.
     */
    private void handleClearChat() {
        chatManager.clearMessages();
        System.out.println("✓ Chat history cleared.");
    }

    /**
     * Show current status.
     */
    private void handleStatus() {
        System.out.println("\n═══ Server Status ═══");
        System.out.println("Connected students: " + students.size());

        if (!students.isEmpty()) {
            System.out.println("\nStudents:");
            students.values().forEach(s -> System.out.println("  - " + s.name + " (ID: " + s.id + ")"));
        }

        Poll poll = pollManager.getCurrentPoll();
        if (poll != null) {
            System.out.println("\nCurrent Poll:");
            System.out.println("  ID: " + poll.id);
            System.out.println("  Question: " + poll.question);
            System.out.println("  Status: " + (poll.active ? "ACTIVE" : "INACTIVE"));
            System.out.println("  Revealed: " + (poll.revealed ? "YES" : "NO"));
            System.out.println("  Total votes: " + poll.getTotalVotes());

            System.out.println("\n  Votes breakdown:");
            for (int i = 0; i < poll.options.length; i++) {
                char letter = (char) ('A' + i);
                int count = poll.getCount(i);
                System.out.println("    " + letter + ": " + count + " votes");
            }
        } else {
            System.out.println("\nNo poll created yet.");
        }

        // Chat status
        ChatManager.ChatStats chatStats = chatManager.getStats();
        System.out.println("\nChat Status:");
        System.out.println("  Enabled: " + (chatStats.enabled ? "YES" : "NO"));
        System.out.println("  Total messages: " + chatStats.totalMessages);
        System.out.println("  Active listeners: " + chatStats.activeListeners);

        System.out.println("═══════════════════\n");
    }

    /**
     * Print help message.
     */
    private void printHelp() {
        System.out.println("\n╔═══ Instructor Commands ═══════════════════════════════════╗");
        System.out.println("║ newpoll <Q> | <A;B;C;D> | <correct>  - Create new poll   ║");
        System.out.println("║ startpoll                            - Activate poll      ║");
        System.out.println("║ endpoll                              - End poll           ║");
        System.out.println("║ reveal                               - Reveal answer      ║");
        System.out.println("║ enablechat                           - Enable chat        ║");
        System.out.println("║ disablechat                          - Disable chat       ║");
        System.out.println("║ clearchat                            - Clear chat history ║");
        System.out.println("║ status                               - Show status        ║");
        System.out.println("║ help                                 - Show this help     ║");
        System.out.println("║ exit                                 - Shutdown server    ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }

    /**
     * Shutdown server gracefully.
     */
    private void handleExit() {
        System.out.println("\n[MainServer] Shutting down...");
        running = false;

        // Close all client connections
        handlers.values().forEach(ClientHandler::close);

        // Shutdown thread pool
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }

        // Stop HTTP server
        dashboard.stop();
        if (assignmentUploadServer != null) assignmentUploadServer.stop();

        // Close server socket
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignore
        }

        System.out.println("[MainServer] Server stopped. Goodbye!");
        System.exit(0);
    }

    /**
     * Register a student client.
     */
    public void registerStudent(String id, Student student, ClientHandler handler) {
        students.put(id, student);
        handlers.put(id, handler);
        System.out.println("[MainServer] Student registered: " + student.name + " (Total: " + students.size() + ")");
    }

    /**
     * Unregister a student client.
     */
    public void unregisterStudent(String id) {
        Student student = students.remove(id);
        handlers.remove(id);
        if (student != null) {
            System.out.println(
                    "[MainServer] Student unregistered: " + student.name + " (Total: " + students.size() + ")");
        }
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        try {
            MainServer server = new MainServer();
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
