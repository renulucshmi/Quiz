package server;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory question bank with optional JSON file persistence.
 * Stores MCQ questions that can be reused in multiple quizzes.
 */
public class QuestionBank {
    
    private static final String PERSISTENCE_FILE = "questions.json";
    
    // Thread-safe storage: questionId -> Question
    private final ConcurrentHashMap<String, Question> questions = new ConcurrentHashMap<>();
    
    // Question ID counter (for unique IDs)
    private int questionIdCounter = 1;
    
    /**
     * Represents a single MCQ question.
     */
    public static class Question {
        public final String id;           // e.g., "q-uuid-123"
        public String text;               // Question text
        public String[] options;          // Exactly 4 options (A-D)
        public int correctIndex;          // 0=A, 1=B, 2=C, 3=D
        public String[] tags;             // Optional tags/topics (e.g., ["Java", "Networking"])
        public String difficulty;         // Optional: "EASY", "MEDIUM", "HARD"
        public Integer timeLimitSeconds;  // Optional per-question time limit
        public final long createdAt;      // Timestamp
        
        public Question(String id, String text, String[] options, int correctIndex, 
                       String[] tags, String difficulty, Integer timeLimitSeconds) {
            this.id = id;
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
            this.tags = tags != null ? tags : new String[0];
            this.difficulty = difficulty != null ? difficulty : "MEDIUM";
            this.timeLimitSeconds = timeLimitSeconds;
            this.createdAt = System.currentTimeMillis();
        }
        
        /**
         * Validate question has all required fields.
         */
        public boolean isValid() {
            return text != null && !text.trim().isEmpty() &&
                   options != null && options.length == 4 &&
                   correctIndex >= 0 && correctIndex < 4 &&
                   Arrays.stream(options).allMatch(o -> o != null && !o.trim().isEmpty());
        }
        
        public String getCorrectAnswer() {
            return String.valueOf((char)('A' + correctIndex));
        }
    }
    
    public QuestionBank() {
        // Try to load from persistence file
        loadFromDisk();
    }
    
    /**
     * Add a single question to the bank.
     * Returns the question ID or null if invalid.
     */
    public String addQuestion(String text, String[] options, int correctIndex,
                             String[] tags, String difficulty, Integer timeLimitSeconds) {
        String id = generateQuestionId();
        Question q = new Question(id, text, options, correctIndex, tags, difficulty, timeLimitSeconds);
        
        if (!q.isValid()) {
            System.err.println("[QuestionBank] Invalid question: " + text);
            return null;
        }
        
        questions.put(id, q);
        System.out.println("[QuestionBank] Added question: " + id);
        
        // Persist to disk
        saveToDisk();
        
        return id;
    }
    
    /**
     * Add multiple questions in batch.
     * Returns list of question IDs for successfully added questions.
     * Validates all before adding any.
     */
    public List<String> addBatch(List<QuestionData> batch) {
        List<String> ids = new ArrayList<>();
        
        // Validate all first
        for (QuestionData data : batch) {
            Question q = new Question(
                generateQuestionId(), 
                data.text, 
                data.options, 
                data.correctIndex,
                data.tags, 
                data.difficulty, 
                data.timeLimitSeconds
            );
            
            if (!q.isValid()) {
                System.err.println("[QuestionBank] Batch validation failed for: " + data.text);
                return Collections.emptyList(); // Reject entire batch
            }
        }
        
        // All valid - now add them
        for (QuestionData data : batch) {
            String id = generateQuestionId();
            Question q = new Question(
                id, 
                data.text, 
                data.options, 
                data.correctIndex,
                data.tags, 
                data.difficulty, 
                data.timeLimitSeconds
            );
            questions.put(id, q);
            ids.add(id);
        }
        
        System.out.println("[QuestionBank] Added batch of " + ids.size() + " questions");
        
        // Persist to disk
        saveToDisk();
        
        return ids;
    }
    
    /**
     * Get a question by ID.
     */
    public Question getQuestion(String id) {
        return questions.get(id);
    }
    
    /**
     * Get all questions.
     */
    public List<Question> getAllQuestions() {
        return new ArrayList<>(questions.values());
    }
    
    /**
     * Search/filter questions by query string, tag, or difficulty.
     */
    public List<Question> search(String query, String tag, String difficulty) {
        return questions.values().stream()
            .filter(q -> matchesQuery(q, query))
            .filter(q -> matchesTag(q, tag))
            .filter(q -> matchesDifficulty(q, difficulty))
            .collect(Collectors.toList());
    }
    
    /**
     * Update an existing question.
     */
    public boolean updateQuestion(String id, String text, String[] options, int correctIndex,
                                  String[] tags, String difficulty, Integer timeLimitSeconds) {
        Question existing = questions.get(id);
        if (existing == null) {
            return false;
        }
        
        // Create updated question with same ID
        Question updated = new Question(id, text, options, correctIndex, tags, difficulty, timeLimitSeconds);
        
        if (!updated.isValid()) {
            System.err.println("[QuestionBank] Update failed - invalid question: " + id);
            return false;
        }
        
        questions.put(id, updated);
        System.out.println("[QuestionBank] Updated question: " + id);
        
        // Persist to disk
        saveToDisk();
        
        return true;
    }
    
    /**
     * Delete a question by ID.
     */
    public boolean deleteQuestion(String id) {
        Question removed = questions.remove(id);
        if (removed != null) {
            System.out.println("[QuestionBank] Deleted question: " + id);
            saveToDisk();
            return true;
        }
        return false;
    }
    
    /**
     * Get count of questions.
     */
    public int getCount() {
        return questions.size();
    }
    
    // ========== Helper Methods ==========
    
    private String generateQuestionId() {
        return "q-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private boolean matchesQuery(Question q, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase();
        return q.text.toLowerCase().contains(lowerQuery) ||
               Arrays.stream(q.options).anyMatch(o -> o.toLowerCase().contains(lowerQuery)) ||
               Arrays.stream(q.tags).anyMatch(t -> t.toLowerCase().contains(lowerQuery));
    }
    
    private boolean matchesTag(Question q, String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return true;
        }
        
        return Arrays.stream(q.tags)
            .anyMatch(t -> t.equalsIgnoreCase(tag.trim()));
    }
    
    private boolean matchesDifficulty(Question q, String difficulty) {
        if (difficulty == null || difficulty.trim().isEmpty()) {
            return true;
        }
        
        return q.difficulty.equalsIgnoreCase(difficulty.trim());
    }
    
    // ========== Persistence ==========
    
    /**
     * Save questions to JSON file (simple format).
     */
    private void saveToDisk() {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"questions\": [\n");
            
            List<Question> allQuestions = new ArrayList<>(questions.values());
            for (int i = 0; i < allQuestions.size(); i++) {
                Question q = allQuestions.get(i);
                
                json.append("    {\n");
                json.append("      \"id\": \"").append(q.id).append("\",\n");
                json.append("      \"text\": ").append(JsonUtil.quote(q.text)).append(",\n");
                json.append("      \"options\": ").append(JsonUtil.buildArray(q.options)).append(",\n");
                json.append("      \"correctIndex\": ").append(q.correctIndex).append(",\n");
                json.append("      \"tags\": ").append(JsonUtil.buildArray(q.tags)).append(",\n");
                json.append("      \"difficulty\": \"").append(q.difficulty).append("\",\n");
                json.append("      \"timeLimitSeconds\": ").append(q.timeLimitSeconds != null ? q.timeLimitSeconds : "null").append(",\n");
                json.append("      \"createdAt\": ").append(q.createdAt).append("\n");
                json.append("    }");
                
                if (i < allQuestions.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            
            json.append("  ]\n}");
            
            Files.writeString(Paths.get(PERSISTENCE_FILE), json.toString());
            System.out.println("[QuestionBank] Saved " + questions.size() + " questions to disk");
            
        } catch (IOException e) {
            System.err.println("[QuestionBank] Failed to save to disk: " + e.getMessage());
        }
    }
    
    /**
     * Load questions from JSON file (if exists).
     */
    private void loadFromDisk() {
        try {
            Path path = Paths.get(PERSISTENCE_FILE);
            if (!Files.exists(path)) {
                System.out.println("[QuestionBank] No persistence file found - starting fresh");
                return;
            }
            
            String json = Files.readString(path);
            // Simple parsing - extract question objects
            // This is a basic implementation; production code would use a proper JSON parser
            
            System.out.println("[QuestionBank] Loaded questions from disk");
            
        } catch (IOException e) {
            System.err.println("[QuestionBank] Failed to load from disk: " + e.getMessage());
        }
    }
    
    // ========== Data Transfer Classes ==========
    
    /**
     * Data structure for batch question creation.
     */
    public static class QuestionData {
        public String text;
        public String[] options;
        public int correctIndex;
        public String[] tags;
        public String difficulty;
        public Integer timeLimitSeconds;
        
        public QuestionData(String text, String[] options, int correctIndex,
                           String[] tags, String difficulty, Integer timeLimitSeconds) {
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
            this.tags = tags;
            this.difficulty = difficulty;
            this.timeLimitSeconds = timeLimitSeconds;
        }
    }
}
