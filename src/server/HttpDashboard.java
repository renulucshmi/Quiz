package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import server.Models.PollStats;
import server.Models.Poll;
import server.Models.Student;

/**
 * Embedded HTTP server for the web dashboard.
 * Serves static files and provides JSON endpoints for student/instructor UIs.
 */
public class HttpDashboard {

    private final HttpServer server;
    private final PollManager pollManager;
    private final ChatManager chatManager;
    private final int port;
    private final ConcurrentHashMap<String, Student> students;

    // Track web-based students separately
    private final ConcurrentHashMap<String, String> webStudents = new ConcurrentHashMap<>();

    public HttpDashboard(int port, PollManager pollManager, ChatManager chatManager,
            ConcurrentHashMap<String, Student> students) throws IOException {
        this.port = port;
        this.pollManager = pollManager;
        this.chatManager = chatManager;
        this.students = students;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register handlers
        server.createContext("/", new StaticFileHandler());
        server.createContext("/stats", new StatsHandler());

        // Student endpoints
        server.createContext("/student/join", new StudentJoinHandler());
        server.createContext("/student/poll", new StudentPollHandler());
        server.createContext("/student/answer", new StudentAnswerHandler());

        // Instructor endpoints
        server.createContext("/instructor/create", new InstructorCreateHandler());
        server.createContext("/instructor/start", new InstructorStartHandler());
        server.createContext("/instructor/end", new InstructorEndHandler());
        server.createContext("/instructor/reveal", new InstructorRevealHandler());
        server.createContext("/instructor/stats", new InstructorStatsHandler());

        // Chat endpoints
        server.createContext("/chat/messages", new ChatMessagesHandler());
        server.createContext("/chat/send", new ChatSendHandler());
        server.createContext("/chat/enable", new ChatEnableHandler());
        server.createContext("/chat/disable", new ChatDisableHandler());
        server.createContext("/chat/clear", new ChatClearHandler());
        server.createContext("/chat/status", new ChatStatusHandler());

        server.setExecutor(null); // Use default executor
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        server.start();
        System.out.println("[HttpDashboard] Dashboard server started on port " + port);
        System.out.println("[HttpDashboard] Open http://localhost:" + port + "/ in your browser");
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(0);
        System.out.println("[HttpDashboard] Dashboard server stopped");
    }

    /**
     * Handler for /stats endpoint - returns JSON poll statistics.
     */
    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");

            try {
                PollStats stats = pollManager.getStats();
                String json = buildStatsJson(stats);

                byte[] response = json.getBytes("UTF-8");
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();

            } catch (Exception e) {
                String error = "{\"error\":\"" + e.getMessage() + "\"}";
                byte[] response = error.getBytes("UTF-8");
                exchange.sendResponseHeaders(500, response.length);
                exchange.getResponseBody().write(response);
                exchange.getResponseBody().close();
            }
        }

        private String buildStatsJson(PollStats stats) {
            if (!stats.active && stats.question == null) {
                return JsonUtil.buildObject(
                        "active", false,
                        "message", "No active poll");
            }

            return JsonUtil.buildObject(
                    "active", stats.active,
                    "pollId", stats.pollId,
                    "question", stats.question,
                    "options", stats.options,
                    "counts", stats.counts,
                    "percentages", stats.percentages,
                    "totalVotes", stats.totalVotes,
                    "revealed", stats.revealed,
                    "correct", stats.correctChoice);
        }
    }

    /**
     * Handler for static files (HTML, CSS, JS).
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Default to index.html
            if ("/".equals(path)) {
                path = "/index.html";
            }

            // Serve from web/ directory
            String filename = "web" + path;
            Path filePath = Paths.get(filename);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String contentType = getContentType(filename);
                exchange.getResponseHeaders().add("Content-Type", contentType);

                byte[] content = Files.readAllBytes(filePath);
                exchange.sendResponseHeaders(200, content.length);
                OutputStream os = exchange.getResponseBody();
                os.write(content);
                os.close();
            } else {
                // 404 Not Found
                String notFound = "<html><body><h1>404 Not Found</h1><p>File not found: " + path + "</p></body></html>";
                byte[] response = notFound.getBytes("UTF-8");
                exchange.sendResponseHeaders(404, response.length);
                exchange.getResponseBody().write(response);
                exchange.getResponseBody().close();
            }
        }

        private String getContentType(String filename) {
            if (filename.endsWith(".html"))
                return "text/html";
            if (filename.endsWith(".css"))
                return "text/css";
            if (filename.endsWith(".js"))
                return "application/javascript";
            if (filename.endsWith(".json"))
                return "application/json";
            return "text/plain";
        }
    }

    /**
     * Student endpoint: POST /student/join
     * Request: {"name": "Alice"}
     * Response: {"success": true, "message": "Welcome, Alice!"}
     */
    private class StudentJoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String body = readBody(exchange);
                Map<String, String> data = parseJson(body);
                String name = data.get("name");

                if (name == null || name.trim().isEmpty()) {
                    sendError(exchange, 400, "Name is required");
                    return;
                }

                name = name.trim();

                // Check if name already exists
                if (students.containsKey(name)) {
                    sendError(exchange, 409, "Name already taken");
                    return;
                }

                // Register web student
                Student student = new Student(name, null);
                students.put(name, student);
                webStudents.put(name, name);

                System.out.println("[Web] Student joined: " + name);

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Welcome, " + name + "!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Student endpoint: GET /student/poll?name=Alice
     * Response: {"active": true, "pollId": "q1", "question": "...", "options":
     * ["A", "B", "C", "D"]}
     */
    private class StudentPollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String name = getQueryParam(query, "name");

                if (name == null || !students.containsKey(name)) {
                    sendError(exchange, 401, "Not logged in");
                    return;
                }

                Poll currentPoll = pollManager.getCurrentPoll();

                if (currentPoll == null || !currentPoll.active) {
                    String response = JsonUtil.buildObject(
                            "active", false,
                            "message", "No active poll");
                    sendJson(exchange, 200, response);
                    return;
                }

                String response = JsonUtil.buildObject(
                        "active", true,
                        "pollId", currentPoll.id,
                        "question", currentPoll.question,
                        "options", currentPoll.options);
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Student endpoint: POST /student/answer
     * Request: {"name": "Alice", "pollId": "q1", "choice": "B"}
     * Response: {"success": true, "message": "Answer submitted"}
     */
    private class StudentAnswerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String body = readBody(exchange);
                Map<String, String> data = parseJson(body);

                String name = data.get("name");
                String pollId = data.get("pollId");
                String choice = data.get("choice");

                if (name == null || !students.containsKey(name)) {
                    sendError(exchange, 401, "Not logged in");
                    return;
                }

                if (pollId == null || choice == null) {
                    sendError(exchange, 400, "Missing pollId or choice");
                    return;
                }

                Poll currentPoll = pollManager.getCurrentPoll();

                if (currentPoll == null || !currentPoll.id.equals(pollId)) {
                    sendError(exchange, 400, "Invalid poll");
                    return;
                }

                if (!currentPoll.active) {
                    sendError(exchange, 400, "Poll is not active");
                    return;
                }

                Student student = students.get(name);
                if (student.currentPollAnswered != null && student.currentPollAnswered.equals(pollId)) {
                    sendError(exchange, 400, "Already answered");
                    return;
                }

                student.currentPollAnswered = pollId;
                pollManager.tallyAnswer(pollId, choice);

                System.out.println("[Web] " + name + " answered: " + choice);

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Answer submitted successfully!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Instructor endpoint: POST /instructor/create
     * Request: {"question": "...", "options": ["A", "B", "C", "D"], "correct": "B"}
     * Response: {"success": true, "pollId": "q1"}
     */
    private class InstructorCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String body = readBody(exchange);
                Map<String, Object> data = parseJsonObject(body);

                String question = (String) data.get("question");
                String[] options = parseOptionsArray(data.get("options"));
                String correct = (String) data.get("correct");

                if (question == null || options == null || correct == null) {
                    sendError(exchange, 400, "Missing required fields");
                    return;
                }

                if (options.length != 4) {
                    sendError(exchange, 400, "Must provide exactly 4 options");
                    return;
                }

                // Convert correct choice letter to index
                int correctIndex = Poll.choiceToIndex(correct);
                if (correctIndex < 0 || correctIndex >= 4) {
                    sendError(exchange, 400, "Correct answer must be A, B, C, or D");
                    return;
                }

                Poll poll = pollManager.createPoll(question, options, correctIndex, 300);

                System.out.println("[Web] Poll created: " + poll.id);

                String response = JsonUtil.buildObject(
                        "success", true,
                        "pollId", poll.id,
                        "message", "Poll created successfully!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Instructor endpoint: POST /instructor/start
     * Request: {}
     * Response: {"success": true}
     */
    private class InstructorStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                Poll poll = pollManager.getCurrentPoll();

                if (poll == null) {
                    sendError(exchange, 400, "No poll to start");
                    return;
                }

                if (poll.active) {
                    sendError(exchange, 400, "Poll already active");
                    return;
                }

                pollManager.startPoll();

                // Reset all students' answers
                for (Student student : students.values()) {
                    student.currentPollAnswered = null;
                }

                System.out.println("[Web] Poll started");

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Poll started!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Instructor endpoint: POST /instructor/end
     * Request: {}
     * Response: {"success": true}
     */
    private class InstructorEndHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                Poll poll = pollManager.getCurrentPoll();

                if (poll == null) {
                    sendError(exchange, 400, "No poll to end");
                    return;
                }

                if (!poll.active) {
                    sendError(exchange, 400, "Poll not active");
                    return;
                }

                pollManager.endPoll();

                System.out.println("[Web] Poll ended");

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Poll ended!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Instructor endpoint: POST /instructor/reveal
     * Request: {}
     * Response: {"success": true}
     */
    private class InstructorRevealHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                Poll poll = pollManager.getCurrentPoll();

                if (poll == null) {
                    sendError(exchange, 400, "No poll to reveal");
                    return;
                }

                if (poll.active) {
                    sendError(exchange, 400, "End poll before revealing");
                    return;
                }

                pollManager.revealAnswer();

                System.out.println("[Web] Answer revealed");

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Answer revealed!");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Instructor endpoint: GET /instructor/stats
     * Response: {"students": [...], "totalStudents": 5, "totalVotes": 3,
     * "pollsCreated": 1}
     */
    private class InstructorStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                PollStats stats = pollManager.getStats();

                // Get student list
                String studentsJson = "[";
                boolean first = true;
                for (Student s : students.values()) {
                    if (!first)
                        studentsJson += ",";
                    first = false;

                    Poll currentPoll = pollManager.getCurrentPoll();
                    boolean answered = currentPoll != null &&
                            s.currentPollAnswered != null &&
                            s.currentPollAnswered.equals(currentPoll.id);

                    studentsJson += JsonUtil.buildObject(
                            "name", s.name,
                            "answered", answered);
                }
                studentsJson += "]";

                String response = String.format(
                        "{\"students\":%s,\"totalStudents\":%d,\"totalVotes\":%d,\"pollsCreated\":1,\"currentPoll\":%s}",
                        studentsJson,
                        students.size(),
                        stats.totalVotes,
                        buildCurrentPollJson(stats));

                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }

        private String buildCurrentPollJson(PollStats stats) {
            if (!stats.active && stats.question == null) {
                return "null";
            }

            return JsonUtil.buildObject(
                    "active", stats.active,
                    "pollId", stats.pollId,
                    "question", stats.question,
                    "revealed", stats.revealed);
        }
    }

    // ========== Helper Methods ==========

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
        BufferedReader br = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private Map<String, String> parseJson(String json) {
        Map<String, String> map = new java.util.HashMap<>();

        // Simple JSON parser for key-value pairs
        json = json.trim();
        if (json.startsWith("{"))
            json = json.substring(1);
        if (json.endsWith("}"))
            json = json.substring(0, json.length() - 1);

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }

        return map;
    }

    private Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> map = new java.util.HashMap<>();

        json = json.trim();
        if (json.startsWith("{"))
            json = json.substring(1);
        if (json.endsWith("}"))
            json = json.substring(0, json.length() - 1);

        // Handle nested arrays
        int bracketLevel = 0;
        StringBuilder currentPair = new StringBuilder();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '[')
                bracketLevel++;
            if (c == ']')
                bracketLevel--;

            if (c == ',' && bracketLevel == 0) {
                parsePair(currentPair.toString(), map);
                currentPair = new StringBuilder();
            } else {
                currentPair.append(c);
            }
        }

        if (currentPair.length() > 0) {
            parsePair(currentPair.toString(), map);
        }

        return map;
    }

    private void parsePair(String pair, Map<String, Object> map) {
        String[] kv = pair.split(":", 2);
        if (kv.length == 2) {
            String key = kv[0].trim().replace("\"", "");
            String value = kv[1].trim();

            if (value.startsWith("[") && value.endsWith("]")) {
                // Array value
                map.put(key, value);
            } else {
                // String value
                map.put(key, value.replace("\"", ""));
            }
        }
    }

    private String[] parseOptionsArray(Object optionsObj) {
        if (optionsObj == null)
            return null;

        String optionsStr = optionsObj.toString();
        if (!optionsStr.startsWith("[") || !optionsStr.endsWith("]")) {
            return null;
        }

        optionsStr = optionsStr.substring(1, optionsStr.length() - 1);
        String[] parts = optionsStr.split(",");
        String[] options = new String[parts.length];

        for (int i = 0; i < parts.length; i++) {
            options[i] = parts[i].trim().replace("\"", "");
        }

        return options;
    }

    private String getQueryParam(String query, String param) {
        if (query == null)
            return null;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return kv[1];
            }
        }
        return null;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = JsonUtil.buildObject("error", message);
        sendJson(exchange, statusCode, json);
    }

    // ========== Chat HTTP Handlers ==========

    /**
     * GET /chat/messages?limit=50
     * Returns recent chat messages
     */
    private class ChatMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String limitStr = getQueryParam(query, "limit");
                int limit = limitStr != null ? Integer.parseInt(limitStr) : 50;

                java.util.List<server.Models.ChatMessage> messages = chatManager.getRecentMessages(limit);

                // Build JSON array
                StringBuilder json = new StringBuilder("{\"messages\":[");
                for (int i = 0; i < messages.size(); i++) {
                    if (i > 0)
                        json.append(",");
                    server.Models.ChatMessage msg = messages.get(i);
                    json.append(JsonUtil.buildObject(
                            "id", msg.id,
                            "username", msg.username,
                            "message", msg.message,
                            "timestamp", msg.timestamp));
                }
                json.append("],\"enabled\":").append(chatManager.isChatEnabled()).append("}");

                sendJson(exchange, 200, json.toString());

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * POST /chat/send
     * Request: {"name": "Alice", "message": "Hello!"}
     * Response: {"success": true}
     */
    private class ChatSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String body = readBody(exchange);
                Map<String, String> data = parseJson(body);

                String name = data.get("name");
                String message = data.get("message");

                if (name == null || !students.containsKey(name)) {
                    sendError(exchange, 401, "Not logged in");
                    return;
                }

                if (message == null || message.trim().isEmpty()) {
                    sendError(exchange, 400, "Message cannot be empty");
                    return;
                }

                server.Models.ChatMessage chatMessage = chatManager.postMessage(name, message);

                if (chatMessage == null) {
                    sendError(exchange, 403, "Chat is currently disabled");
                    return;
                }

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Message sent");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * POST /chat/enable
     * Enable chat (instructor only)
     */
    private class ChatEnableHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                chatManager.enableChat();

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Chat enabled");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * POST /chat/disable
     * Disable chat (instructor only)
     */
    private class ChatDisableHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                chatManager.disableChat();

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Chat disabled");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * POST /chat/clear
     * Clear all messages (instructor only)
     */
    private class ChatClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                chatManager.clearMessages();

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Chat cleared");
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /chat/status
     * Returns chat status
     */
    private class ChatStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                ChatManager.ChatStats stats = chatManager.getStats();

                String response = JsonUtil.buildObject(
                        "enabled", stats.enabled,
                        "totalMessages", stats.totalMessages,
                        "activeListeners", stats.activeListeners);
                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }
}