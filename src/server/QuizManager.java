package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe quiz management.
 * Handles quiz creation, storage, and lifecycle.
 */
public class QuizManager {
    
    // Storage: quizId -> Quiz
    private final ConcurrentHashMap<String, Quiz> quizzes = new ConcurrentHashMap<>();
    
    // Current active quiz runtime
    private volatile QuizRuntime activeRuntime = null;
    
    /**
     * Quiz status enumeration.
     */
    public enum QuizStatus {
        DRAFT,      // Being created
        READY,      // Ready to launch
        RUNNING,    // Currently active
        ENDED       // Completed
    }
    
    /**
     * Represents a quiz - a collection of questions with metadata.
     */
    public static class Quiz {
        public final String id;
        public String title;
        public String description;
        public List<String> questionIds;        // Ordered list of question IDs
        public Integer perQuestionTime;         // Default time per question (seconds)
        public Integer totalTime;               // Total quiz time limit (seconds)
        public int perCorrectScore;             // Points per correct answer
        public QuizStatus status;
        public final long createdAt;
        
        public Quiz(String id, String title, String description, List<String> questionIds,
                   Integer perQuestionTime, Integer totalTime, int perCorrectScore) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.questionIds = new ArrayList<>(questionIds);
            this.perQuestionTime = perQuestionTime;
            this.totalTime = totalTime;
            this.perCorrectScore = perCorrectScore > 0 ? perCorrectScore : 1;
            this.status = QuizStatus.DRAFT;
            this.createdAt = System.currentTimeMillis();
        }
        
        public int getQuestionCount() {
            return questionIds.size();
        }
    }
    
    /**
     * Create a new quiz.
     */
    public Quiz createQuiz(String title, String description, List<String> questionIds,
                          Integer perQuestionTime, Integer totalTime, Integer perCorrectScore) {
        if (questionIds == null || questionIds.isEmpty()) {
            System.err.println("[QuizManager] Cannot create quiz without questions");
            return null;
        }
        
        String id = generateQuizId();
        Quiz quiz = new Quiz(id, title, description, questionIds, 
                           perQuestionTime, totalTime, 
                           perCorrectScore != null ? perCorrectScore : 1);
        
        quizzes.put(id, quiz);
        System.out.println("[QuizManager] Created quiz: " + id + " - " + title + 
                         " (" + questionIds.size() + " questions)");
        
        return quiz;
    }
    
    /**
     * Get a quiz by ID.
     */
    public Quiz getQuiz(String quizId) {
        return quizzes.get(quizId);
    }
    
    /**
     * Get all quizzes.
     */
    public List<Quiz> getAllQuizzes() {
        return new ArrayList<>(quizzes.values());
    }
    
    /**
     * Update an existing quiz.
     */
    public boolean updateQuiz(String quizId, String title, String description, 
                             List<String> questionIds, Integer perQuestionTime, 
                             Integer totalTime, Integer perCorrectScore) {
        Quiz existing = quizzes.get(quizId);
        if (existing == null) {
            return false;
        }
        
        // Cannot update running or ended quiz
        if (existing.status == QuizStatus.RUNNING || existing.status == QuizStatus.ENDED) {
            System.err.println("[QuizManager] Cannot update quiz in status: " + existing.status);
            return false;
        }
        
        if (title != null) existing.title = title;
        if (description != null) existing.description = description;
        if (questionIds != null && !questionIds.isEmpty()) existing.questionIds = new ArrayList<>(questionIds);
        if (perQuestionTime != null) existing.perQuestionTime = perQuestionTime;
        if (totalTime != null) existing.totalTime = totalTime;
        if (perCorrectScore != null) existing.perCorrectScore = perCorrectScore;
        
        System.out.println("[QuizManager] Updated quiz: " + quizId);
        return true;
    }
    
    /**
     * Delete a quiz.
     */
    public boolean deleteQuiz(String quizId) {
        Quiz quiz = quizzes.get(quizId);
        if (quiz == null) {
            return false;
        }
        
        // Cannot delete running quiz
        if (quiz.status == QuizStatus.RUNNING) {
            System.err.println("[QuizManager] Cannot delete running quiz");
            return false;
        }
        
        quizzes.remove(quizId);
        System.out.println("[QuizManager] Deleted quiz: " + quizId);
        return true;
    }
    
    /**
     * Mark quiz as ready to launch.
     */
    public boolean markReady(String quizId) {
        Quiz quiz = quizzes.get(quizId);
        if (quiz == null) {
            return false;
        }
        
        quiz.status = QuizStatus.READY;
        System.out.println("[QuizManager] Quiz marked as READY: " + quizId);
        return true;
    }
    
    /**
     * Launch a quiz (creates runtime state).
     */
    public QuizRuntime launchQuiz(String quizId, QuestionBank questionBank) {
        Quiz quiz = quizzes.get(quizId);
        if (quiz == null) {
            System.err.println("[QuizManager] Quiz not found: " + quizId);
            return null;
        }
        
        if (quiz.status == QuizStatus.RUNNING) {
            System.err.println("[QuizManager] Quiz already running");
            return activeRuntime;
        }
        
        if (activeRuntime != null) {
            System.err.println("[QuizManager] Another quiz is already running");
            return null;
        }
        
        // Create runtime
        activeRuntime = new QuizRuntime(quiz, questionBank);
        quiz.status = QuizStatus.RUNNING;
        
        System.out.println("[QuizManager] Launched quiz: " + quizId);
        return activeRuntime;
    }
    
    /**
     * Get current active quiz runtime.
     */
    public QuizRuntime getActiveRuntime() {
        return activeRuntime;
    }
    
    /**
     * End the current quiz.
     */
    public boolean endQuiz() {
        if (activeRuntime == null) {
            return false;
        }
        
        Quiz quiz = quizzes.get(activeRuntime.quizId);
        if (quiz != null) {
            quiz.status = QuizStatus.ENDED;
        }
        
        activeRuntime.end();
        System.out.println("[QuizManager] Quiz ended: " + activeRuntime.quizId);
        activeRuntime = null;
        
        return true;
    }
    
    /**
     * Get quiz count by status.
     */
    public int getCountByStatus(QuizStatus status) {
        return (int) quizzes.values().stream()
            .filter(q -> q.status == status)
            .count();
    }
    
    // ========== Helper Methods ==========
    
    private String generateQuizId() {
        return "quiz-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
