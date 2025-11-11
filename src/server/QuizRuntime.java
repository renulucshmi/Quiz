package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime state for an active quiz.
 * Tracks current question, timing, student submissions, and scores.
 */
public class QuizRuntime {
    
    public final String quizId;
    public final QuizManager.Quiz quiz;
    public final QuestionBank questionBank;
    
    // Current question index (0-based)
    private final AtomicInteger currentIndex = new AtomicInteger(-1);
    
    // Quiz start time
    private final long startTimeMillis;
    
    // Current question start time
    private volatile long currentQuestionStartTime = 0;
    
    // Student submissions: questionId -> (studentName -> choice)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> submissions = new ConcurrentHashMap<>();
    
    // Student scores: studentName -> score
    private final ConcurrentHashMap<String, AtomicInteger> scores = new ConcurrentHashMap<>();
    
    // State flags
    private volatile boolean ended = false;
    private volatile boolean currentQuestionRevealed = false;
    
    public QuizRuntime(QuizManager.Quiz quiz, QuestionBank questionBank) {
        this.quiz = quiz;
        this.quizId = quiz.id;
        this.questionBank = questionBank;
        this.startTimeMillis = System.currentTimeMillis();
        
        System.out.println("[QuizRuntime] Created runtime for quiz: " + quiz.title);
    }
    
    /**
     * Start the first question.
     */
    public QuestionBank.Question startFirstQuestion() {
        currentIndex.set(0);
        currentQuestionStartTime = System.currentTimeMillis();
        currentQuestionRevealed = false;
        
        String questionId = quiz.questionIds.get(0);
        QuestionBank.Question question = questionBank.getQuestion(questionId);
        
        // Initialize submissions map for this question
        submissions.put(questionId, new ConcurrentHashMap<>());
        
        System.out.println("[QuizRuntime] Started question 1/" + quiz.questionIds.size());
        return question;
    }
    
    /**
     * Move to next question.
     * Returns the next question or null if quiz is complete.
     */
    public QuestionBank.Question nextQuestion() {
        int current = currentIndex.get();
        if (current + 1 >= quiz.questionIds.size()) {
            System.out.println("[QuizRuntime] No more questions - quiz complete");
            return null;
        }
        
        // Move to next
        int next = currentIndex.incrementAndGet();
        currentQuestionStartTime = System.currentTimeMillis();
        currentQuestionRevealed = false;
        
        String questionId = quiz.questionIds.get(next);
        QuestionBank.Question question = questionBank.getQuestion(questionId);
        
        // Initialize submissions map for this question
        submissions.put(questionId, new ConcurrentHashMap<>());
        
        System.out.println("[QuizRuntime] Started question " + (next + 1) + "/" + quiz.questionIds.size());
        return question;
    }
    
    /**
     * Get current question.
     */
    public QuestionBank.Question getCurrentQuestion() {
        int index = currentIndex.get();
        if (index < 0 || index >= quiz.questionIds.size()) {
            return null;
        }
        
        String questionId = quiz.questionIds.get(index);
        return questionBank.getQuestion(questionId);
    }
    
    /**
     * Get current question index (0-based).
     */
    public int getCurrentIndex() {
        return currentIndex.get();
    }
    
    /**
     * Submit an answer for current question.
     */
    public boolean submitAnswer(String studentName, String choice) {
        if (ended) {
            System.err.println("[QuizRuntime] Quiz has ended - answer rejected");
            return false;
        }
        
        int index = currentIndex.get();
        if (index < 0 || index >= quiz.questionIds.size()) {
            System.err.println("[QuizRuntime] No active question - answer rejected");
            return false;
        }
        
        String questionId = quiz.questionIds.get(index);
        QuestionBank.Question question = questionBank.getQuestion(questionId);
        
        if (question == null) {
            System.err.println("[QuizRuntime] Question not found: " + questionId);
            return false;
        }
        
        // Check if already answered
        ConcurrentHashMap<String, String> questionSubmissions = submissions.get(questionId);
        if (questionSubmissions.containsKey(studentName)) {
            System.out.println("[QuizRuntime] " + studentName + " already answered question " + questionId);
            return false;
        }
        
        // Record answer
        questionSubmissions.put(studentName, choice);
        
        // Update score if correct
        int choiceIndex = choice.charAt(0) - 'A';
        if (choiceIndex == question.correctIndex) {
            scores.computeIfAbsent(studentName, k -> new AtomicInteger(0))
                  .addAndGet(quiz.perCorrectScore);
            System.out.println("[QuizRuntime] " + studentName + " answered CORRECTLY");
        } else {
            // Ensure student has entry in scores
            scores.computeIfAbsent(studentName, k -> new AtomicInteger(0));
            System.out.println("[QuizRuntime] " + studentName + " answered incorrectly");
        }
        
        return true;
    }
    
    /**
     * Reveal current question answer.
     */
    public void revealCurrentQuestion() {
        currentQuestionRevealed = true;
        System.out.println("[QuizRuntime] Current question answer revealed");
    }
    
    /**
     * Check if current question is revealed.
     */
    public boolean isCurrentQuestionRevealed() {
        return currentQuestionRevealed;
    }
    
    /**
     * Get answer counts for current question.
     */
    public Map<String, Integer> getCurrentQuestionCounts() {
        int index = currentIndex.get();
        if (index < 0 || index >= quiz.questionIds.size()) {
            return Collections.emptyMap();
        }
        
        String questionId = quiz.questionIds.get(index);
        ConcurrentHashMap<String, String> questionSubmissions = submissions.get(questionId);
        
        if (questionSubmissions == null) {
            return Collections.emptyMap();
        }
        
        // Count each choice
        Map<String, Integer> counts = new HashMap<>();
        counts.put("A", 0);
        counts.put("B", 0);
        counts.put("C", 0);
        counts.put("D", 0);
        
        for (String choice : questionSubmissions.values()) {
            counts.put(choice, counts.getOrDefault(choice, 0) + 1);
        }
        
        return counts;
    }
    
    /**
     * Get leaderboard (sorted by score).
     */
    public List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        
        for (Map.Entry<String, AtomicInteger> entry : scores.entrySet()) {
            leaderboard.add(new LeaderboardEntry(entry.getKey(), entry.getValue().get()));
        }
        
        // Sort by score descending
        leaderboard.sort((a, b) -> Integer.compare(b.score, a.score));
        
        return leaderboard;
    }
    
    /**
     * Get a student's score.
     */
    public int getStudentScore(String studentName) {
        AtomicInteger score = scores.get(studentName);
        return score != null ? score.get() : 0;
    }
    
    /**
     * Check if quiz time has exceeded.
     */
    public boolean isQuizTimeExceeded() {
        if (quiz.totalTime == null) {
            return false;
        }
        
        long elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000;
        return elapsed > quiz.totalTime;
    }
    
    /**
     * Check if current question time has exceeded.
     */
    public boolean isQuestionTimeExceeded() {
        QuestionBank.Question current = getCurrentQuestion();
        if (current == null) {
            return false;
        }
        
        Integer timeLimit = current.timeLimitSeconds != null ? 
                          current.timeLimitSeconds : quiz.perQuestionTime;
        
        if (timeLimit == null) {
            return false;
        }
        
        long elapsed = (System.currentTimeMillis() - currentQuestionStartTime) / 1000;
        return elapsed > timeLimit;
    }
    
    /**
     * Get remaining time for current question (in seconds).
     */
    public Integer getRemainingQuestionTime() {
        QuestionBank.Question current = getCurrentQuestion();
        if (current == null) {
            return null;
        }
        
        Integer timeLimit = current.timeLimitSeconds != null ? 
                          current.timeLimitSeconds : quiz.perQuestionTime;
        
        if (timeLimit == null) {
            return null;
        }
        
        long elapsed = (System.currentTimeMillis() - currentQuestionStartTime) / 1000;
        int remaining = (int)(timeLimit - elapsed);
        
        return Math.max(0, remaining);
    }
    
    /**
     * End the quiz.
     */
    public void end() {
        ended = true;
        System.out.println("[QuizRuntime] Quiz ended - final scores:");
        
        List<LeaderboardEntry> leaderboard = getLeaderboard();
        for (int i = 0; i < Math.min(10, leaderboard.size()); i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            System.out.println("  " + (i + 1) + ". " + entry.studentName + ": " + entry.score + " points");
        }
    }
    
    /**
     * Check if quiz has ended.
     */
    public boolean isEnded() {
        return ended;
    }
    
    /**
     * Get total number of questions.
     */
    public int getTotalQuestions() {
        return quiz.questionIds.size();
    }
    
    /**
     * Get number of students who have participated.
     */
    public int getParticipantCount() {
        return scores.size();
    }
    
    // ========== Data Classes ==========
    
    /**
     * Leaderboard entry.
     */
    public static class LeaderboardEntry {
        public final String studentName;
        public final int score;
        
        public LeaderboardEntry(String studentName, int score) {
            this.studentName = studentName;
            this.score = score;
        }
    }
}
