package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import server.Models.Student;

/**
 * Embedded HTTP server for the web dashboard.
 * Serves static files and provides JSON endpoints for student/instructor UIs.
 */
public class HttpDashboard {

    private final HttpServer server;
    private final ChatManager chatManager;
    private final QAManager qaManager;
    private final QuestionBank questionBank;
    private final QuizManager quizManager;
    private final VoteManager voteManager;
    private final int port;
    private final ConcurrentHashMap<String, Student> students;
    private final ConcurrentHashMap<String, ClientHandler> handlers;

    // Track web-based students separately
    private final ConcurrentHashMap<String, String> webStudents = new ConcurrentHashMap<>();

    public HttpDashboard(int port, ChatManager chatManager, QAManager qaManager,
            QuestionBank questionBank, QuizManager quizManager,
            ConcurrentHashMap<String, Student> students,
            ConcurrentHashMap<String, ClientHandler> handlers) throws IOException {
        this.port = port;
        this.chatManager = chatManager;
        this.qaManager = qaManager;
        this.questionBank = questionBank;
        this.quizManager = quizManager;
        this.voteManager = VoteManager.getInstance();
        this.students = students;
        this.handlers = handlers;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register API handlers FIRST (before static file handler)
        // Student endpoints
        server.createContext("/student/join", new StudentJoinHandler());

        // Chat endpoints
        server.createContext("/chat/messages", new ChatMessagesHandler());
        server.createContext("/chat/send", new ChatSendHandler());
        server.createContext("/chat/enable", new ChatEnableHandler());
        server.createContext("/chat/disable", new ChatDisableHandler());
        server.createContext("/chat/clear", new ChatClearHandler());
        server.createContext("/chat/status", new ChatStatusHandler());

        // Q&A endpoints
        server.createContext("/qa/ask", new QASubmitHandler());
        server.createContext("/qa/questions", new QAListHandler());
        server.createContext("/qa/reply", new QAReplyHandler());
        server.createContext("/qa/delete", new QADeleteHandler());
        server.createContext("/qa/student", new QAStudentHandler());
        System.out.println("[HttpDashboard] Q&A endpoints registered");

        // Question Bank endpoints (new)
        server.createContext("/api/questions/batch", new QuestionBatchHandler());
        server.createContext("/api/questions", new QuestionsHandler());
        
        // Quiz endpoints (new)
        server.createContext("/api/quizzes", new QuizzesHandler());
        
        // Vote endpoints (new)
        server.createContext("/api/votes/create", new VoteCreateHandler());
        server.createContext("/api/votes/", new VoteActionHandler()); // Handles /{id}/open, /{id}/close, /{id}/status
        server.createContext("/api/votes", new VotesListHandler()); // List all votes

        // Static file handler LAST (catches everything else)
        server.createContext("/", new StaticFileHandler());

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
     * Handler for static files (HTML, CSS, JS).
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            System.out.println("[StaticFileHandler] Handling request: " + path);

            // Skip API endpoints - let other handlers process them
            if (path.startsWith("/student/") || path.startsWith("/instructor/") ||
                    path.startsWith("/chat/") || path.startsWith("/qa/") ||
                    path.equals("/stats")) {
                // This shouldn't happen if contexts are registered correctly,
                // but as a fallback, return 404
                System.out.println("[StaticFileHandler] ERROR: API path reached static handler: " + path);
                String notFound = "<html><body><h1>404 Not Found</h1><p>API endpoint not found: " + path
                        + "</p></body></html>";
                byte[] response = notFound.getBytes("UTF-8");
                exchange.sendResponseHeaders(404, response.length);
                exchange.getResponseBody().write(response);
                exchange.getResponseBody().close();
                return;
            }

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

    // ========== Helper Methods ==========

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"));
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

                // Allow instructor or any student to send messages
                if (name == null || name.trim().isEmpty()) {
                    sendError(exchange, 401, "Not logged in");
                    return;
                }

                // Only check if student is logged in if they're not the instructor
                if (!name.equals("Instructor") && !students.containsKey(name)) {
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

    // ==================== Q&A Handlers ====================

    /**
     * POST /qa/ask
     * Student submits a question
     * Body: {"studentName": "Alice", "question": "..."}
     */
    private class QASubmitHandler implements HttpHandler {
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
                String body = new String(exchange.getRequestBody().readAllBytes());
                Map<String, String> data = JsonUtil.parseObject(body);

                String studentName = data.get("studentName");
                String question = data.get("question");

                if (studentName == null || studentName.trim().isEmpty()) {
                    sendError(exchange, 400, "Student name is required");
                    return;
                }

                if (question == null || question.trim().isEmpty()) {
                    sendError(exchange, 400, "Question is required");
                    return;
                }

                QAManager.Question q = qaManager.submitQuestion(studentName, question);

                String response = JsonUtil.buildObject(
                        "success", true,
                        "questionId", q.id,
                        "message", "Question submitted successfully");

                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /qa/questions
     * Get all questions (for instructor)
     */
    private class QAListHandler implements HttpHandler {
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
                List<QAManager.Question> questions = qaManager.getAllQuestions();

                StringBuilder sb = new StringBuilder();
                sb.append("{\"questions\":[");

                for (int i = 0; i < questions.size(); i++) {
                    QAManager.Question q = questions.get(i);
                    if (i > 0)
                        sb.append(",");

                    sb.append("{");
                    sb.append("\"id\":").append(q.id).append(",");
                    sb.append("\"studentName\":").append(JsonUtil.quote(q.studentName)).append(",");
                    sb.append("\"question\":").append(JsonUtil.quote(q.question)).append(",");
                    sb.append("\"timestamp\":").append(q.timestamp).append(",");
                    sb.append("\"status\":").append(JsonUtil.quote(q.status.toString())).append(",");
                    sb.append("\"reply\":").append(q.reply != null ? JsonUtil.quote(q.reply) : "null").append(",");
                    sb.append("\"replyTimestamp\":").append(q.replyTimestamp != null ? q.replyTimestamp : "null");
                    sb.append("}");
                }

                sb.append("],");
                sb.append("\"pendingCount\":").append(qaManager.getPendingCount());
                sb.append("}");

                sendJson(exchange, 200, sb.toString());

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * POST /qa/reply
     * Instructor replies to a question
     * Body: {"questionId": 1, "reply": "..."}
     */
    private class QAReplyHandler implements HttpHandler {
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
                String body = new String(exchange.getRequestBody().readAllBytes());
                Map<String, String> data = JsonUtil.parseObject(body);

                String qIdStr = data.get("questionId");
                String reply = data.get("reply");

                if (qIdStr == null) {
                    sendError(exchange, 400, "Question ID is required");
                    return;
                }

                int questionId = Integer.parseInt(qIdStr);

                if (reply == null || reply.trim().isEmpty()) {
                    sendError(exchange, 400, "Reply is required");
                    return;
                }

                QAManager.Question updatedQuestion = qaManager.replyToQuestion(questionId, reply);

                if (updatedQuestion == null) {
                    sendError(exchange, 404, "Question not found");
                    return;
                }

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Reply sent successfully");

                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * DELETE /qa/delete?id=1
     * Instructor deletes a question
     */
    private class QADeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"DELETE".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                if (query == null) {
                    sendError(exchange, 400, "Question ID is required");
                    return;
                }

                int questionId = -1;
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && kv[0].equals("id")) {
                        questionId = Integer.parseInt(kv[1]);
                        break;
                    }
                }

                if (questionId == -1) {
                    sendError(exchange, 400, "Invalid question ID");
                    return;
                }

                boolean deleted = qaManager.deleteQuestion(questionId);

                if (!deleted) {
                    sendError(exchange, 404, "Question not found");
                    return;
                }

                String response = JsonUtil.buildObject(
                        "success", true,
                        "message", "Question deleted");

                sendJson(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * GET /qa/student?name=Alice
     * Get questions for a specific student
     */
    private class QAStudentHandler implements HttpHandler {
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
                System.out.println("[QAStudentHandler] Received request with query: " + query);

                if (query == null) {
                    System.err.println("[QAStudentHandler] No query string provided");
                    sendError(exchange, 400, "Student name is required");
                    return;
                }

                String studentName = null;
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && kv[0].equals("name")) {
                        studentName = java.net.URLDecoder.decode(kv[1], "UTF-8");
                        break;
                    }
                }

                System.out.println("[QAStudentHandler] Extracted student name: " + studentName);

                if (studentName == null || studentName.trim().isEmpty()) {
                    System.err.println("[QAStudentHandler] Student name is empty");
                    sendError(exchange, 400, "Student name is required");
                    return;
                }

                List<QAManager.Question> questions = qaManager.getStudentQuestions(studentName);
                System.out.println("[QAStudentHandler] Found " + questions.size() + " questions for " + studentName);

                StringBuilder sb = new StringBuilder();
                sb.append("{\"questions\":[");

                for (int i = 0; i < questions.size(); i++) {
                    QAManager.Question q = questions.get(i);
                    if (i > 0)
                        sb.append(",");

                    sb.append("{");
                    sb.append("\"id\":").append(q.id).append(",");
                    sb.append("\"studentName\":").append(JsonUtil.quote(q.studentName)).append(",");
                    sb.append("\"question\":").append(JsonUtil.quote(q.question)).append(",");
                    sb.append("\"timestamp\":").append(q.timestamp).append(",");
                    sb.append("\"status\":").append(JsonUtil.quote(q.status.toString())).append(",");
                    sb.append("\"reply\":").append(q.reply != null ? JsonUtil.quote(q.reply) : "null").append(",");
                    sb.append("\"replyTimestamp\":").append(q.replyTimestamp != null ? q.replyTimestamp : "null");
                    sb.append("}");
                }

                sb.append("]}");

                sendJson(exchange, 200, sb.toString());
                System.out.println("[QAStudentHandler] Response sent successfully");

            } catch (Exception e) {
                System.err.println("[QAStudentHandler] Error: " + e.getMessage());
                e.printStackTrace();
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    // ==================== Question Bank Handlers ====================

    /**
     * POST /api/questions/batch
     * Create multiple questions at once
     * Body: [{question, options[4], correctIndex, tags[], difficulty, timeLimitSeconds?}, ...]
     */
    private class QuestionBatchHandler implements HttpHandler {
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
                String body = new String(exchange.getRequestBody().readAllBytes());
                
                // Parse JSON array
                List<QuestionBank.QuestionData> batch = parseBatchQuestions(body);
                
                if (batch.isEmpty()) {
                    sendError(exchange, 400, "No valid questions in batch");
                    return;
                }
                
                // Add batch to question bank
                List<String> ids = questionBank.addBatch(batch);
                
                if (ids.isEmpty()) {
                    sendError(exchange, 400, "Batch validation failed - check all fields");
                    return;
                }
                
                // Build response with IDs
                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Added ").append(ids.size()).append(" questions\",");
                json.append("\"questionIds\":[");
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(ids.get(i)).append("\"");
                }
                json.append("]}");
                
                sendJson(exchange, 200, json.toString());
                
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for individual question operations:
     * GET /api/questions?q=search&tag=Java&difficulty=EASY
     * GET /api/questions/{id}
     * PUT /api/questions/{id}
     * DELETE /api/questions/{id}
     */
    private class QuestionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            try {
                if ("GET".equals(method)) {
                    handleGetQuestions(exchange);
                } else if ("PUT".equals(method)) {
                    handleUpdateQuestion(exchange, path);
                } else if ("DELETE".equals(method)) {
                    handleDeleteQuestion(exchange, path);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
        
        private void handleGetQuestions(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            
            // Check if requesting specific question: /api/questions/{id}
            String[] parts = path.split("/");
            if (parts.length > 3 && !parts[3].isEmpty()) {
                String questionId = parts[3];
                QuestionBank.Question q = questionBank.getQuestion(questionId);
                
                if (q == null) {
                    sendError(exchange, 404, "Question not found");
                    return;
                }
                
                String json = buildQuestionJson(q);
                sendJson(exchange, 200, json);
                return;
            }
            
            // List/search questions
            String searchQuery = getQueryParam(query, "q");
            String tag = getQueryParam(query, "tag");
            String difficulty = getQueryParam(query, "difficulty");
            
            List<QuestionBank.Question> questions = questionBank.search(searchQuery, tag, difficulty);
            
            StringBuilder json = new StringBuilder();
            json.append("{\"questions\":[");
            
            for (int i = 0; i < questions.size(); i++) {
                if (i > 0) json.append(",");
                json.append(buildQuestionJson(questions.get(i)));
            }
            
            json.append("],\"count\":").append(questions.size()).append("}");
            
            sendJson(exchange, 200, json.toString());
        }
        
        private void handleUpdateQuestion(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length <= 3 || parts[3].isEmpty()) {
                sendError(exchange, 400, "Question ID required");
                return;
            }
            
            String questionId = parts[3];
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, Object> data = parseQuestionData(body);
            
            String text = (String) data.get("text");
            String[] options = (String[]) data.get("options");
            Integer correctIndex = (Integer) data.get("correctIndex");
            String[] tags = (String[]) data.get("tags");
            String difficulty = (String) data.get("difficulty");
            Integer timeLimitSeconds = (Integer) data.get("timeLimitSeconds");
            
            boolean updated = questionBank.updateQuestion(questionId, text, options, 
                correctIndex != null ? correctIndex : 0, tags, difficulty, timeLimitSeconds);
            
            if (!updated) {
                sendError(exchange, 404, "Question not found or invalid");
                return;
            }
            
            String response = JsonUtil.buildObject("success", true, "message", "Question updated");
            sendJson(exchange, 200, response);
        }
        
        private void handleDeleteQuestion(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length <= 3 || parts[3].isEmpty()) {
                sendError(exchange, 400, "Question ID required");
                return;
            }
            
            String questionId = parts[3];
            boolean deleted = questionBank.deleteQuestion(questionId);
            
            if (!deleted) {
                sendError(exchange, 404, "Question not found");
                return;
            }
            
            String response = JsonUtil.buildObject("success", true, "message", "Question deleted");
            sendJson(exchange, 200, response);
        }
    }

    /**
     * Handler for quiz operations:
     * POST /api/quizzes - create quiz
     * GET /api/quizzes - list all quizzes
     * GET /api/quizzes/{id} - get quiz details
     * PUT /api/quizzes/{id} - update quiz
     * DELETE /api/quizzes/{id} - delete quiz
     * POST /api/quizzes/{id}/launch - launch quiz
     * POST /api/quizzes/{id}/next - next question
     * POST /api/quizzes/{id}/end - end quiz
     * POST /api/quizzes/{id}/revealCurrent - reveal current answer
     * POST /api/quizzes/{id}/submit - submit student answer
     * GET /api/quizzes/{id}/status - get quiz status
     */
    private class QuizzesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            try {
                if ("POST".equals(method)) {
                    handlePostQuiz(exchange, path);
                } else if ("GET".equals(method)) {
                    handleGetQuiz(exchange, path);
                } else if ("PUT".equals(method)) {
                    handlePutQuiz(exchange, path);
                } else if ("DELETE".equals(method)) {
                    handleDeleteQuiz(exchange, path);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
        
        private void handlePostQuiz(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            
            // Check for action endpoints: /api/quizzes/{id}/{action}
            if (parts.length >= 5) {
                String quizId = parts[3];
                String action = parts[4];
                
                switch (action) {
                    case "launch":
                        handleLaunchQuiz(exchange, quizId);
                        return;
                    case "next":
                        handleNextQuestion(exchange, quizId);
                        return;
                    case "end":
                        handleEndQuiz(exchange, quizId);
                        return;
                    case "revealCurrent":
                        handleRevealCurrent(exchange, quizId);
                        return;
                    case "submit":
                        handleSubmitAnswer(exchange, quizId);
                        return;
                    default:
                        sendError(exchange, 404, "Unknown action: " + action);
                        return;
                }
            }
            
            // Create new quiz
            handleCreateQuiz(exchange);
        }
        
        private void handleCreateQuiz(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, Object> data = parseQuizData(body);
            
            String title = (String) data.get("title");
            String description = (String) data.get("description");
            List<String> questionIds = (List<String>) data.get("questionIds");
            Integer perQuestionTime = (Integer) data.get("perQuestionTime");
            Integer totalTime = (Integer) data.get("totalTime");
            Integer perCorrectScore = (Integer) data.get("perCorrectScore");
            
            if (title == null || questionIds == null || questionIds.isEmpty()) {
                sendError(exchange, 400, "Title and questionIds required");
                return;
            }
            
            QuizManager.Quiz quiz = quizManager.createQuiz(title, description, questionIds, 
                perQuestionTime, totalTime, perCorrectScore);
            
            if (quiz == null) {
                sendError(exchange, 400, "Failed to create quiz");
                return;
            }
            
            String response = JsonUtil.buildObject(
                "success", true,
                "quizId", quiz.id,
                "message", "Quiz created successfully"
            );
            sendJson(exchange, 200, response);
        }
        
        private void handleGetQuiz(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            
            // Check for status endpoint: /api/quizzes/{id}/status
            if (parts.length >= 5 && "status".equals(parts[4])) {
                handleGetStatus(exchange, parts[3]);
                return;
            }
            
            // Check if requesting specific quiz: /api/quizzes/{id}
            if (parts.length > 3 && !parts[3].isEmpty()) {
                String quizId = parts[3];
                QuizManager.Quiz quiz = quizManager.getQuiz(quizId);
                
                if (quiz == null) {
                    sendError(exchange, 404, "Quiz not found");
                    return;
                }
                
                String json = buildQuizJson(quiz);
                sendJson(exchange, 200, json);
                return;
            }
            
            // List all quizzes
            List<QuizManager.Quiz> quizzes = quizManager.getAllQuizzes();
            
            StringBuilder json = new StringBuilder();
            json.append("{\"quizzes\":[");
            
            for (int i = 0; i < quizzes.size(); i++) {
                if (i > 0) json.append(",");
                json.append(buildQuizJson(quizzes.get(i)));
            }
            
            json.append("],\"count\":").append(quizzes.size()).append("}");
            
            sendJson(exchange, 200, json.toString());
        }
        
        private void handlePutQuiz(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length <= 3 || parts[3].isEmpty()) {
                sendError(exchange, 400, "Quiz ID required");
                return;
            }
            
            String quizId = parts[3];
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, Object> data = parseQuizData(body);
            
            String title = (String) data.get("title");
            String description = (String) data.get("description");
            List<String> questionIds = (List<String>) data.get("questionIds");
            Integer perQuestionTime = (Integer) data.get("perQuestionTime");
            Integer totalTime = (Integer) data.get("totalTime");
            Integer perCorrectScore = (Integer) data.get("perCorrectScore");
            
            boolean updated = quizManager.updateQuiz(quizId, title, description, questionIds,
                perQuestionTime, totalTime, perCorrectScore);
            
            if (!updated) {
                sendError(exchange, 404, "Quiz not found or cannot be updated");
                return;
            }
            
            String response = JsonUtil.buildObject("success", true, "message", "Quiz updated");
            sendJson(exchange, 200, response);
        }
        
        private void handleDeleteQuiz(HttpExchange exchange, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length <= 3 || parts[3].isEmpty()) {
                sendError(exchange, 400, "Quiz ID required");
                return;
            }
            
            String quizId = parts[3];
            boolean deleted = quizManager.deleteQuiz(quizId);
            
            if (!deleted) {
                sendError(exchange, 404, "Quiz not found or cannot be deleted");
                return;
            }
            
            String response = JsonUtil.buildObject("success", true, "message", "Quiz deleted");
            sendJson(exchange, 200, response);
        }
        
        private void handleLaunchQuiz(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.launchQuiz(quizId, questionBank);
            
            if (runtime == null) {
                sendError(exchange, 400, "Cannot launch quiz - quiz not found or already running");
                return;
            }
            
            // Start first question
            QuestionBank.Question firstQuestion = runtime.startFirstQuestion();
            
            if (firstQuestion == null) {
                // End the runtime since we can't start
                quizManager.endQuiz();
                sendError(exchange, 400, "Quiz references a question that doesn't exist in the question bank. Please ensure all questions are created before launching the quiz.");
                return;
            }
            
            // TODO: Broadcast to TCP clients (would need ClientHandler integration)
            
            String response = JsonUtil.buildObject(
                "success", true,
                "message", "Quiz launched",
                "currentIndex", 0,
                "totalQuestions", runtime.getTotalQuestions()
            );
            sendJson(exchange, 200, response);
        }
        
        private void handleNextQuestion(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.getActiveRuntime();
            
            if (runtime == null || !runtime.quizId.equals(quizId)) {
                sendError(exchange, 400, "No active quiz");
                return;
            }
            
            QuestionBank.Question nextQuestion = runtime.nextQuestion();
            
            if (nextQuestion == null) {
                // Quiz complete
                quizManager.endQuiz();
                String response = JsonUtil.buildObject(
                    "success", true,
                    "message", "Quiz completed",
                    "completed", true
                );
                sendJson(exchange, 200, response);
                return;
            }
            
            // TODO: Broadcast to TCP clients
            
            String response = JsonUtil.buildObject(
                "success", true,
                "message", "Advanced to next question",
                "currentIndex", runtime.getCurrentIndex(),
                "totalQuestions", runtime.getTotalQuestions()
            );
            sendJson(exchange, 200, response);
        }
        
        private void handleEndQuiz(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.getActiveRuntime();
            
            if (runtime == null || !runtime.quizId.equals(quizId)) {
                sendError(exchange, 400, "No active quiz");
                return;
            }
            
            quizManager.endQuiz();
            
            String response = JsonUtil.buildObject(
                "success", true,
                "message", "Quiz ended"
            );
            sendJson(exchange, 200, response);
        }
        
        private void handleRevealCurrent(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.getActiveRuntime();
            
            if (runtime == null || !runtime.quizId.equals(quizId)) {
                sendError(exchange, 400, "No active quiz");
                return;
            }
            
            runtime.revealCurrentQuestion();
            
            // Broadcast reveal to all connected TCP students
            broadcastQuizReveal(quizId, runtime);
            
            String response = JsonUtil.buildObject(
                "success", true,
                "message", "Answer revealed"
            );
            sendJson(exchange, 200, response);
        }
        
        private void handleSubmitAnswer(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.getActiveRuntime();
            
            if (runtime == null || !runtime.quizId.equals(quizId)) {
                System.err.println("[QuizSubmit] No active quiz or quiz ID mismatch");
                sendError(exchange, 400, "No active quiz");
                return;
            }
            
            // Parse request body
            String body = new String(exchange.getRequestBody().readAllBytes());
            System.out.println("[QuizSubmit] Received body: " + body);
            
            String studentName = extractStringField(body, "studentName");
            String questionId = extractStringField(body, "questionId");
            String choice = extractStringField(body, "choice");
            
            System.out.println("[QuizSubmit] Parsed - studentName: " + studentName + ", questionId: " + questionId + ", choice: " + choice);
            
            if (studentName == null || questionId == null || choice == null) {
                System.err.println("[QuizSubmit] Missing required fields");
                sendError(exchange, 400, "studentName, questionId, and choice are required");
                return;
            }
            
            // Validate choice is A, B, C, or D
            if (!choice.matches("[A-D]")) {
                sendError(exchange, 400, "Choice must be A, B, C, or D");
                return;
            }
            
            // Validate question ID matches current question
            QuestionBank.Question currentQuestion = runtime.getCurrentQuestion();
            if (currentQuestion == null || !currentQuestion.id.equals(questionId)) {
                sendError(exchange, 400, "Invalid question ID (not the current question)");
                return;
            }
            
            // Submit answer
            boolean success = runtime.submitAnswer(studentName, choice);
            
            if (success) {
                System.out.println("[QuizRuntime] " + studentName + " answered " + choice + " for question " + questionId);
                
                String response = JsonUtil.buildObject(
                    "success", true,
                    "message", "Answer submitted successfully"
                );
                sendJson(exchange, 200, response);
            } else {
                sendError(exchange, 400, "Failed to submit answer (already answered or invalid question)");
            }
        }
        
        private void handleGetStatus(HttpExchange exchange, String quizId) throws IOException {
            QuizRuntime runtime = quizManager.getActiveRuntime();
            
            if (runtime == null || !runtime.quizId.equals(quizId)) {
                sendError(exchange, 400, "No active quiz");
                return;
            }
            
            // Get query parameters to check for studentName
            String query = exchange.getRequestURI().getQuery();
            String studentName = null;
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "studentName".equals(keyValue[0])) {
                        try {
                            studentName = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            
            QuestionBank.Question current = runtime.getCurrentQuestion();
            Map<String, Integer> counts = runtime.getCurrentQuestionCounts();
            
            // Calculate response count
            int responseCount = counts.values().stream().mapToInt(Integer::intValue).sum();
            
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"quizId\":\"").append(runtime.quizId).append("\",");
            json.append("\"currentIndex\":").append(runtime.getCurrentIndex()).append(",");
            json.append("\"totalQuestions\":").append(runtime.getTotalQuestions()).append(",");
            json.append("\"revealed\":").append(runtime.isCurrentQuestionRevealed()).append(",");
            json.append("\"participantCount\":").append(runtime.getParticipantCount()).append(",");
            json.append("\"responseCount\":").append(responseCount).append(",");
            
            // Include student's score if studentName is provided
            if (studentName != null && !studentName.isEmpty()) {
                int studentScore = runtime.getStudentScore(studentName);
                json.append("\"studentScore\":").append(studentScore).append(",");
            }
            
            if (current != null) {
                json.append("\"currentQuestion\":{");
                json.append("\"id\":\"").append(current.id).append("\",");
                json.append("\"text\":").append(JsonUtil.quote(current.text)).append(",");
                json.append("\"options\":").append(JsonUtil.buildArray(current.options));
                
                // Include correctIndex if revealed
                if (runtime.isCurrentQuestionRevealed()) {
                    json.append(",\"correctIndex\":").append(current.correctIndex);
                }
                
                json.append("},");
            }
            
            json.append("\"counts\":{");
            json.append("\"A\":").append(counts.getOrDefault("A", 0)).append(",");
            json.append("\"B\":").append(counts.getOrDefault("B", 0)).append(",");
            json.append("\"C\":").append(counts.getOrDefault("C", 0)).append(",");
            json.append("\"D\":").append(counts.getOrDefault("D", 0));
            json.append("},");
            
            // Add leaderboard data
            json.append("\"leaderboard\":[");
            List<QuizRuntime.LeaderboardEntry> leaderboard = runtime.getLeaderboard();
            for (int i = 0; i < leaderboard.size(); i++) {
                if (i > 0) json.append(",");
                QuizRuntime.LeaderboardEntry entry = leaderboard.get(i);
                json.append("{");
                json.append("\"rank\":").append(i + 1).append(",");
                json.append("\"name\":").append(JsonUtil.quote(entry.studentName)).append(",");
                json.append("\"score\":").append(entry.score);
                json.append("}");
            }
            json.append("]");
            
            json.append("}");
            
            sendJson(exchange, 200, json.toString());
        }
    }

    // ========== Helper Methods for New Endpoints ==========

    private List<QuestionBank.QuestionData> parseBatchQuestions(String json) {
        List<QuestionBank.QuestionData> batch = new ArrayList<>();
        
        // Simple JSON array parser
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) {
            return batch;
        }
        
        json = json.substring(1, json.length() - 1); // Remove [ ]
        
        // Split by objects (simple approach)
        int depth = 0;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '{') depth++;
            if (c == '}') depth--;
            
            current.append(c);
            
            if (depth == 0 && c == '}') {
                // End of object
                Map<String, Object> data = parseQuestionData(current.toString());
                
                String text = (String) data.get("text");
                String[] options = (String[]) data.get("options");
                Integer correctIndex = (Integer) data.get("correctIndex");
                String[] tags = (String[]) data.get("tags");
                String difficulty = (String) data.get("difficulty");
                Integer timeLimitSeconds = (Integer) data.get("timeLimitSeconds");
                
                if (text != null && options != null && correctIndex != null) {
                    batch.add(new QuestionBank.QuestionData(text, options, correctIndex, 
                        tags, difficulty, timeLimitSeconds));
                }
                
                current = new StringBuilder();
                
                // Skip comma
                while (i + 1 < json.length() && (json.charAt(i + 1) == ',' || 
                       Character.isWhitespace(json.charAt(i + 1)))) {
                    i++;
                }
            }
        }
        
        return batch;
    }

    private Map<String, Object> parseQuestionData(String json) {
        Map<String, Object> result = new HashMap<>();
        
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        // Parse fields
        String text = extractStringField(json, "text");
        String[] options = extractArrayField(json, "options");
        Integer correctIndex = extractIntField(json, "correctIndex");
        String[] tags = extractArrayField(json, "tags");
        String difficulty = extractStringField(json, "difficulty");
        Integer timeLimitSeconds = extractIntField(json, "timeLimitSeconds");
        
        if (text != null) result.put("text", text);
        if (options != null) result.put("options", options);
        if (correctIndex != null) result.put("correctIndex", correctIndex);
        if (tags != null) result.put("tags", tags);
        if (difficulty != null) result.put("difficulty", difficulty);
        if (timeLimitSeconds != null) result.put("timeLimitSeconds", timeLimitSeconds);
        
        return result;
    }

    private Map<String, Object> parseQuizData(String json) {
        Map<String, Object> result = new HashMap<>();
        
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        String title = extractStringField(json, "title");
        String description = extractStringField(json, "description");
        String[] questionIdsArray = extractArrayField(json, "questionIds");
        Integer perQuestionTime = extractIntField(json, "perQuestionTime");
        Integer totalTime = extractIntField(json, "totalTime");
        Integer perCorrectScore = extractIntField(json, "perCorrectScore");
        
        if (title != null) result.put("title", title);
        if (description != null) result.put("description", description);
        if (questionIdsArray != null) {
            result.put("questionIds", Arrays.asList(questionIdsArray));
        }
        if (perQuestionTime != null) result.put("perQuestionTime", perQuestionTime);
        if (totalTime != null) result.put("totalTime", totalTime);
        if (perCorrectScore != null) result.put("perCorrectScore", perCorrectScore);
        
        return result;
    }

    private String extractStringField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private Integer extractIntField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*(\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private String[] extractArrayField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            String arrayContent = m.group(1).trim();
            if (arrayContent.isEmpty()) {
                return new String[0];
            }
            
            // Split by commas, handling quoted strings
            List<String> items = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean inQuotes = false;
            
            for (int i = 0; i < arrayContent.length(); i++) {
                char c = arrayContent.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                } else if (c == ',' && !inQuotes) {
                    String item = current.toString().trim();
                    if (!item.isEmpty()) {
                        items.add(item.replace("\"", ""));
                    }
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            
            String item = current.toString().trim();
            if (!item.isEmpty()) {
                items.add(item.replace("\"", ""));
            }
            
            return items.toArray(new String[0]);
        }
        return null;
    }

    private String buildQuestionJson(QuestionBank.Question q) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(q.id).append("\",");
        json.append("\"text\":").append(JsonUtil.quote(q.text)).append(",");
        json.append("\"options\":").append(JsonUtil.buildArray(q.options)).append(",");
        json.append("\"correctIndex\":").append(q.correctIndex).append(",");
        json.append("\"correctAnswer\":\"").append(q.getCorrectAnswer()).append("\",");
        json.append("\"tags\":").append(JsonUtil.buildArray(q.tags)).append(",");
        json.append("\"difficulty\":\"").append(q.difficulty).append("\",");
        json.append("\"timeLimitSeconds\":").append(q.timeLimitSeconds != null ? q.timeLimitSeconds : "null").append(",");
        json.append("\"createdAt\":").append(q.createdAt);
        json.append("}");
        return json.toString();
    }

    private String buildQuizJson(QuizManager.Quiz quiz) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(quiz.id).append("\",");
        json.append("\"title\":").append(JsonUtil.quote(quiz.title)).append(",");
        json.append("\"description\":").append(quiz.description != null ? JsonUtil.quote(quiz.description) : "null").append(",");
        json.append("\"questionCount\":").append(quiz.getQuestionCount()).append(",");
        json.append("\"questionIds\":[");
        for (int i = 0; i < quiz.questionIds.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(quiz.questionIds.get(i)).append("\"");
        }
        json.append("],");
        json.append("\"perQuestionTime\":").append(quiz.perQuestionTime != null ? quiz.perQuestionTime : "null").append(",");
        json.append("\"totalTime\":").append(quiz.totalTime != null ? quiz.totalTime : "null").append(",");
        json.append("\"perCorrectScore\":").append(quiz.perCorrectScore).append(",");
        json.append("\"status\":\"").append(quiz.status).append("\",");
        json.append("\"createdAt\":").append(quiz.createdAt);
        json.append("}");
        return json.toString();
    }

    // ==================== Vote Handler Classes ====================
    
    /**
     * GET /api/votes
     * List all votes with their current state
     */
    private class VotesListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            try {
                Collection<VoteManager.Vote> allVotes = voteManager.getAllVotes();
                
                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"votes\":[");
                
                int count = 0;
                for (VoteManager.Vote vote : allVotes) {
                    if (count > 0) json.append(",");
                    
                    json.append("{");
                    json.append("\"id\":\"").append(vote.getId()).append("\",");
                    json.append("\"question\":").append(JsonUtil.quote(vote.getQuestion())).append(",");
                    json.append("\"options\":[");
                    String[] options = vote.getOptions();
                    for (int i = 0; i < options.length; i++) {
                        if (i > 0) json.append(",");
                        json.append(JsonUtil.quote(options[i]));
                    }
                    json.append("],");
                    json.append("\"state\":\"").append(vote.getState()).append("\",");
                    json.append("\"allowRevote\":").append(vote.isAllowRevote()).append(",");
                    json.append("\"deadline\":").append(vote.getDeadline() != null ? JsonUtil.quote(vote.getDeadline()) : "null");
                    json.append("}");
                    
                    count++;
                }
                
                json.append("]}");
                
                sendJson(exchange, 200, json.toString());

            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"Server error\"}");
            }
        }
    }

    /**
     * POST /api/votes/create
     * Create a new date vote
     */
    private class VoteCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes());
                
                // Parse JSON manually using existing helper methods
                String question = extractStringField(body, "question");
                String[] options = extractArrayField(body, "options");
                boolean allowRevote = extractJsonBoolean(body, "allowRevote", true);
                String deadline = extractStringField(body, "deadline");

                if (question == null || question.trim().isEmpty()) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Question is required\"}");
                    return;
                }

                if (options == null || options.length != 4) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Exactly 4 date/time options required\"}");
                    return;
                }

                // Validate datetime format (ISO 8601)
                for (String opt : options) {
                    if (opt == null || opt.trim().isEmpty()) {
                        sendJson(exchange, 400, "{\"success\":false,\"message\":\"All options must have valid datetime values\"}");
                        return;
                    }
                }

                String voteId = voteManager.createVote(question, options, allowRevote, deadline);
                
                String response = "{\"success\":true,\"voteId\":\"" + voteId + "\"}";
                sendJson(exchange, 200, response);

            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"Server error\"}");
            }
        }
    }

    /**
     * Handles /api/votes/{id}/open, /api/votes/{id}/close, /api/votes/{id}/status
     */
    private class VoteActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // Parse path: /api/votes/{id}/{action}
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendJson(exchange, 400, "{\"success\":false,\"message\":\"Invalid path\"}");
                return;
            }

            String voteId = parts[3]; // e.g., "v1"
            String action = parts.length > 4 ? parts[4] : ""; // e.g., "open", "close", "status"

            try {
                switch (action) {
                    case "open":
                        if (!"POST".equals(method)) {
                            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                            return;
                        }
                        handleOpenVote(exchange, voteId);
                        break;

                    case "close":
                        if (!"POST".equals(method)) {
                            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                            return;
                        }
                        handleCloseVote(exchange, voteId);
                        break;

                    case "status":
                        if (!"GET".equals(method)) {
                            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                            return;
                        }
                        handleVoteStatus(exchange, voteId);
                        break;

                    case "submit":
                        if (!"POST".equals(method)) {
                            sendJson(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                            return;
                        }
                        handleVoteSubmit(exchange, voteId);
                        break;

                    default:
                        sendJson(exchange, 400, "{\"success\":false,\"message\":\"Unknown action\"}");
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"Server error\"}");
            }
        }

        private void handleOpenVote(HttpExchange exchange, String voteId) throws IOException {
            boolean success = voteManager.openVote(voteId);
            if (success) {
                // Broadcast to students via TCP
                broadcastVoteToStudents(voteId);
                sendJson(exchange, 200, "{\"success\":true}");
            } else {
                sendJson(exchange, 404, "{\"success\":false,\"message\":\"Vote not found\"}");
            }
        }

        private void handleCloseVote(HttpExchange exchange, String voteId) throws IOException {
            boolean success = voteManager.closeVote(voteId);
            if (success) {
                // Broadcast close event to students via TCP
                broadcastVoteClosedToStudents(voteId);
                sendJson(exchange, 200, "{\"success\":true}");
            } else {
                sendJson(exchange, 404, "{\"success\":false,\"message\":\"Vote not found\"}");
            }
        }

        private void handleVoteStatus(HttpExchange exchange, String voteId) throws IOException {
            Map<String, Object> status = voteManager.getStatusSnapshot(voteId);
            String json = buildVoteStatusJson(status);
            sendJson(exchange, 200, json);
        }

        private void handleVoteSubmit(HttpExchange exchange, String voteId) throws IOException {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes());
                
                // Parse JSON manually
                String studentName = extractStringField(body, "studentName");
                String choice = extractStringField(body, "choice");

                if (studentName == null || studentName.trim().isEmpty()) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Student name is required\"}");
                    return;
                }

                if (choice == null || choice.trim().isEmpty()) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Choice is required\"}");
                    return;
                }

                // Convert choice (A, B, C, D) to index (0, 1, 2, 3)
                int optionIndex = choice.charAt(0) - 'A';
                if (optionIndex < 0 || optionIndex > 3) {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Invalid choice. Must be A, B, C, or D\"}");
                    return;
                }

                boolean success = voteManager.castVote(voteId, studentName, optionIndex);
                
                if (success) {
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"Vote cast successfully\"}");
                } else {
                    sendJson(exchange, 400, "{\"success\":false,\"message\":\"Failed to cast vote. Vote may be closed or not found.\"}");
                }

            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"Server error\"}");
            }
        }

        private String buildVoteStatusJson(Map<String, Object> status) {
            StringBuilder json = new StringBuilder();
            json.append("{");
            
            Boolean success = (Boolean) status.get("success");
            json.append("\"success\":").append(success != null ? success : false).append(",");
            
            if (success != null && success) {
                json.append("\"state\":\"").append(status.get("state")).append("\",");
                json.append("\"question\":").append(JsonUtil.quote((String) status.get("question"))).append(",");
                
                // Options array
                String[] options = (String[]) status.get("options");
                json.append("\"options\":[");
                for (int i = 0; i < options.length; i++) {
                    if (i > 0) json.append(",");
                    json.append(JsonUtil.quote(options[i]));
                }
                json.append("],");
                
                // Counts array
                int[] counts = (int[]) status.get("counts");
                json.append("\"counts\":[");
                for (int i = 0; i < counts.length; i++) {
                    if (i > 0) json.append(",");
                    json.append(counts[i]);
                }
                json.append("],");
                
                // Percent array
                double[] percent = (double[]) status.get("percent");
                json.append("\"percent\":[");
                for (int i = 0; i < percent.length; i++) {
                    if (i > 0) json.append(",");
                    json.append(String.format("%.2f", percent[i]));
                }
                json.append("],");
                
                // Winner indexes (if closed)
                @SuppressWarnings("unchecked")
                List<Integer> winnerIndexes = (List<Integer>) status.get("winnerIndexes");
                if (winnerIndexes != null) {
                    json.append("\"winnerIndexes\":[");
                    for (int i = 0; i < winnerIndexes.size(); i++) {
                        if (i > 0) json.append(",");
                        json.append(winnerIndexes.get(i));
                    }
                    json.append("]");
                } else {
                    json.setLength(json.length() - 1); // Remove trailing comma
                }
            } else {
                json.append("\"message\":").append(JsonUtil.quote((String) status.get("message")));
            }
            
            json.append("}");
            return json.toString();
        }
    }

    // ========== Quiz Broadcast Methods ==========
    
    private void broadcastQuizReveal(String quizId, QuizRuntime runtime) {
        QuestionBank.Question currentQuestion = runtime.getCurrentQuestion();
        if (currentQuestion == null) return;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"quizReveal\",");
        json.append("\"quizId\":\"").append(quizId).append("\",");
        json.append("\"questionId\":\"").append(currentQuestion.id).append("\",");
        json.append("\"correctIndex\":").append(currentQuestion.correctIndex);
        json.append("}");

        String message = json.toString();
        
        // Broadcast to all connected TCP students
        System.out.println("[QuizManager] Answer revealed for quiz: " + quizId);
        System.out.println("[QuizManager] Broadcasting to " + handlers.size() + " TCP clients");
        for (ClientHandler handler : handlers.values()) {
            handler.sendQuizReveal(message);
        }
    }

    // ========== Vote Broadcast Methods ==========

    // Helper methods for broadcasting vote events to TCP students
    private void broadcastVoteToStudents(String voteId) {
        VoteManager.Vote vote = voteManager.getVote(voteId);
        if (vote == null) return;

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"vote\",");
        json.append("\"id\":\"").append(vote.getId()).append("\",");
        json.append("\"question\":").append(JsonUtil.quote(vote.getQuestion())).append(",");
        json.append("\"options\":[");
        String[] options = vote.getOptions();
        for (int i = 0; i < options.length; i++) {
            if (i > 0) json.append(",");
            json.append(JsonUtil.quote(options[i]));
        }
        json.append("],");
        json.append("\"allowRevote\":").append(vote.isAllowRevote()).append(",");
        json.append("\"deadline\":").append(vote.getDeadline() != null ? JsonUtil.quote(vote.getDeadline()) : "null");
        json.append("}");

        String message = json.toString();
        
        // Broadcast to all connected TCP students
        System.out.println("[VoteManager] Vote opened: " + voteId);
        System.out.println("[VoteManager] Broadcasting to " + handlers.size() + " TCP clients");
        for (ClientHandler handler : handlers.values()) {
            handler.sendVote(message);
        }
    }

    private void broadcastVoteClosedToStudents(String voteId) {
        Map<String, Object> status = voteManager.getStatusSnapshot(voteId);
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"voteClosed\",");
        json.append("\"voteId\":\"").append(voteId).append("\",");
        
        @SuppressWarnings("unchecked")
        List<Integer> winnerIndexes = (List<Integer>) status.get("winnerIndexes");
        if (winnerIndexes != null) {
            json.append("\"winnerIndexes\":[");
            for (int i = 0; i < winnerIndexes.size(); i++) {
                if (i > 0) json.append(",");
                json.append(winnerIndexes.get(i));
            }
            json.append("]");
        }
        json.append("}");

        String message = json.toString();
        
        // Broadcast to all connected TCP students
        System.out.println("[VoteManager] Vote closed: " + voteId);
        System.out.println("[VoteManager] Broadcasting to " + handlers.size() + " TCP clients");
        for (ClientHandler handler : handlers.values()) {
            handler.sendVoteClosed(message);
        }
    }

    // Helper method to extract boolean from JSON
    private boolean extractJsonBoolean(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return defaultValue;
    }

    // Helper method to extract Long from JSON
    private Long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return null;
    }
}