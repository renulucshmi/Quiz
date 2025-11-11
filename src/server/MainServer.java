package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.*;
import server.Models.Student;

/**
 * Main TCP server for the Remote Classroom Polling System.
 * 
 * Features:
 * - Multithreaded TCP server using ExecutorService
 * - Instructor CLI for quiz and vote management
 * - Embedded HTTP server for web dashboard
 * - Thread-safe student registry
 */
public class MainServer {

    private static final int TCP_PORT = 8088;
    private static final int HTTP_PORT = 8090;
    private static final int THREAD_POOL_SIZE = 16;

    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final ChatManager chatManager;
    private final QAManager qaManager;
    private final QuestionBank questionBank;
    private final QuizManager quizManager;
    private final HttpDashboard dashboard;

    // Thread-safe registry of connected students
    private final ConcurrentHashMap<String, Student> students = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientHandler> handlers = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    public MainServer() throws IOException {
        this.serverSocket = new ServerSocket(TCP_PORT);
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.chatManager = new ChatManager();
        this.qaManager = new QAManager();
        this.questionBank = new QuestionBank();
        this.quizManager = new QuizManager();
        this.dashboard = new HttpDashboard(HTTP_PORT, chatManager, qaManager, questionBank, quizManager, students, handlers);

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
                ClientHandler handler = new ClientHandler(clientSocket, chatManager, this);
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