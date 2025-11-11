package server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QAManager - Manages Q&A system for students to ask questions to instructor
 * Separate from the chat/discussion feature
 */
public class QAManager {
    private final Map<Integer, Question> questions;
    private final AtomicInteger questionIdCounter;
    private final List<QAListener> listeners;

    public QAManager() {
        this.questions = new ConcurrentHashMap<>();
        this.questionIdCounter = new AtomicInteger(1);
        this.listeners = new ArrayList<>();
        System.out.println("[QAManager] Q&A system initialized");
    }

    /**
     * Submit a new question from a student
     */
    public synchronized Question submitQuestion(String studentName, String questionText) {
        int questionId = questionIdCounter.getAndIncrement();
        Question question = new Question(
                questionId,
                studentName,
                questionText,
                System.currentTimeMillis(),
                null,
                null,
                QuestionStatus.PENDING);

        questions.put(questionId, question);
        System.out.println("[QAManager] New question #" + questionId + " from " + studentName);

        // Notify listeners
        notifyNewQuestion(question);

        return question;
    }

    /**
     * Instructor replies to a question
     */
    public synchronized Question replyToQuestion(int questionId, String reply) {
        Question question = questions.get(questionId);
        if (question == null) {
            return null;
        }

        Question updatedQuestion = new Question(
                question.id,
                question.studentName,
                question.question,
                question.timestamp,
                reply,
                System.currentTimeMillis(),
                QuestionStatus.ANSWERED);

        questions.put(questionId, updatedQuestion);
        System.out.println("[QAManager] Question #" + questionId + " answered");

        // Notify listeners
        notifyQuestionAnswered(updatedQuestion);

        return updatedQuestion;
    }

    /**
     * Get all questions (for instructor dashboard)
     */
    public List<Question> getAllQuestions() {
        List<Question> questionList = new ArrayList<>(questions.values());
        // Sort by timestamp (newest first)
        questionList.sort((q1, q2) -> Long.compare(q2.timestamp, q1.timestamp));
        return questionList;
    }

    /**
     * Get questions for a specific student
     */
    public List<Question> getStudentQuestions(String studentName) {
        List<Question> studentQuestions = new ArrayList<>();
        for (Question q : questions.values()) {
            if (q.studentName.equals(studentName)) {
                studentQuestions.add(q);
            }
        }
        // Sort by timestamp (newest first)
        studentQuestions.sort((q1, q2) -> Long.compare(q2.timestamp, q1.timestamp));
        return studentQuestions;
    }

    /**
     * Get a specific question by ID
     */
    public Question getQuestion(int questionId) {
        return questions.get(questionId);
    }

    /**
     * Delete a question (instructor only)
     */
    public synchronized boolean deleteQuestion(int questionId) {
        Question removed = questions.remove(questionId);
        if (removed != null) {
            System.out.println("[QAManager] Question #" + questionId + " deleted");
            notifyQuestionDeleted(questionId);
            return true;
        }
        return false;
    }

    /**
     * Get pending questions count
     */
    public int getPendingCount() {
        return (int) questions.values().stream()
                .filter(q -> q.status == QuestionStatus.PENDING)
                .count();
    }

    /**
     * Clear all questions
     */
    public synchronized void clearAllQuestions() {
        questions.clear();
        System.out.println("[QAManager] All questions cleared");
        notifyQuestionsCleared();
    }

    // Listener management
    public void addListener(QAListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(QAListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void notifyNewQuestion(Question question) {
        synchronized (listeners) {
            for (QAListener listener : listeners) {
                listener.onNewQuestion(question);
            }
        }
    }

    private void notifyQuestionAnswered(Question question) {
        synchronized (listeners) {
            for (QAListener listener : listeners) {
                listener.onQuestionAnswered(question);
            }
        }
    }

    private void notifyQuestionDeleted(int questionId) {
        synchronized (listeners) {
            for (QAListener listener : listeners) {
                listener.onQuestionDeleted(questionId);
            }
        }
    }

    private void notifyQuestionsCleared() {
        synchronized (listeners) {
            for (QAListener listener : listeners) {
                listener.onQuestionsCleared();
            }
        }
    }

    /**
     * Question data class
     */
    public static class Question {
        public final int id;
        public final String studentName;
        public final String question;
        public final long timestamp;
        public final String reply;
        public final Long replyTimestamp;
        public final QuestionStatus status;

        public Question(int id, String studentName, String question, long timestamp,
                String reply, Long replyTimestamp, QuestionStatus status) {
            this.id = id;
            this.studentName = studentName;
            this.question = question;
            this.timestamp = timestamp;
            this.reply = reply;
            this.replyTimestamp = replyTimestamp;
            this.status = status;
        }
    }

    /**
     * Question status enum
     */
    public enum QuestionStatus {
        PENDING,
        ANSWERED
    }

    /**
     * Listener interface for Q&A events
     */
    public interface QAListener {
        void onNewQuestion(Question question);

        void onQuestionAnswered(Question question);

        void onQuestionDeleted(int questionId);

        void onQuestionsCleared();
    }
}
