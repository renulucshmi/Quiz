package server;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import server.Models.ChatMessage;

/**
 * Thread-safe chat management for poll discussions.
 * 
 * Features:
 * - Message broadcasting to all connected clients
 * - Message history with limit
 * - Thread-safe operations using concurrent collections
 * - Chat moderation (enable/disable)
 */
public class ChatManager {
    
    private static final int MAX_MESSAGES = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    
    // Thread-safe message storage
    private final ConcurrentLinkedQueue<ChatMessage> messages;
    
    // Listeners for new messages (ClientHandlers)
    private final CopyOnWriteArrayList<ChatListener> listeners;
    
    // Chat state
    private final AtomicBoolean chatEnabled;
    private final AtomicInteger messageIdCounter;
    
    public ChatManager() {
        this.messages = new ConcurrentLinkedQueue<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.chatEnabled = new AtomicBoolean(false); // Disabled by default
        this.messageIdCounter = new AtomicInteger(1);
        
        System.out.println("[ChatManager] Chat system initialized");
    }
    
    /**
     * Post a new chat message.
     * Returns the created message or null if chat is disabled or message is invalid.
     */
    public ChatMessage postMessage(String username, String message) {
        if (!chatEnabled.get()) {
            System.out.println("[ChatManager] Chat disabled, message rejected from: " + username);
            return null;
        }
        
        if (message == null || message.trim().isEmpty()) {
            System.out.println("[ChatManager] Empty message rejected from: " + username);
            return null;
        }
        
        // Trim and validate length
        message = message.trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH);
        }
        
        // Create message
        int id = messageIdCounter.getAndIncrement();
        ChatMessage chatMessage = new ChatMessage(id, username, message);
        
        // Add to queue
        messages.offer(chatMessage);
        
        // Enforce message limit
        while (messages.size() > MAX_MESSAGES) {
            messages.poll();
        }
        
        System.out.println("[ChatManager] Message from " + username + ": " + message);
        
        // Broadcast to all listeners
        broadcastMessage(chatMessage);
        
        return chatMessage;
    }
    
    /**
     * Get recent chat messages (up to limit).
     */
    public List<ChatMessage> getRecentMessages(int limit) {
        List<ChatMessage> result = new ArrayList<>();
        int count = 0;
        
        // Get messages from queue (newest first requires conversion)
        List<ChatMessage> allMessages = new ArrayList<>(messages);
        
        // Return up to limit messages (most recent)
        int start = Math.max(0, allMessages.size() - limit);
        for (int i = start; i < allMessages.size(); i++) {
            result.add(allMessages.get(i));
        }
        
        return result;
    }
    
    /**
     * Get all chat messages.
     */
    public List<ChatMessage> getAllMessages() {
        return new ArrayList<>(messages);
    }
    
    /**
     * Clear all chat messages.
     */
    public void clearMessages() {
        messages.clear();
        System.out.println("[ChatManager] All messages cleared");
        
        // Notify listeners about clear
        for (ChatListener listener : listeners) {
            listener.onChatCleared();
        }
    }
    
    /**
     * Enable chat for discussion.
     */
    public void enableChat() {
        chatEnabled.set(true);
        System.out.println("[ChatManager] Chat enabled for discussion");
        
        // Broadcast system message
        ChatMessage systemMsg = new ChatMessage(
            messageIdCounter.getAndIncrement(),
            "SYSTEM",
            "💬 Chat is now open for discussion!"
        );
        messages.offer(systemMsg);
        broadcastMessage(systemMsg);
    }
    
    /**
     * Disable chat.
     */
    public void disableChat() {
        chatEnabled.set(false);
        System.out.println("[ChatManager] Chat disabled");
        
        // Broadcast system message
        ChatMessage systemMsg = new ChatMessage(
            messageIdCounter.getAndIncrement(),
            "SYSTEM",
            "🔒 Chat has been closed by instructor"
        );
        messages.offer(systemMsg);
        broadcastMessage(systemMsg);
    }
    
    /**
     * Check if chat is currently enabled.
     */
    public boolean isChatEnabled() {
        return chatEnabled.get();
    }
    
    /**
     * Register a listener for new chat messages.
     */
    public void addListener(ChatListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Unregister a listener.
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Broadcast message to all registered listeners.
     */
    private void broadcastMessage(ChatMessage message) {
        for (ChatListener listener : listeners) {
            try {
                listener.onNewMessage(message);
            } catch (Exception e) {
                System.err.println("[ChatManager] Error broadcasting to listener: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get chat statistics.
     */
    public ChatStats getStats() {
        return new ChatStats(
            messages.size(),
            chatEnabled.get(),
            listeners.size()
        );
    }
    
    /**
     * Interface for chat listeners (ClientHandlers).
     */
    public interface ChatListener {
        void onNewMessage(ChatMessage message);
        void onChatCleared();
    }
    
    /**
     * Chat statistics data class.
     */
    public static class ChatStats {
        public final int totalMessages;
        public final boolean enabled;
        public final int activeListeners;
        
        public ChatStats(int totalMessages, boolean enabled, int activeListeners) {
            this.totalMessages = totalMessages;
            this.enabled = enabled;
            this.activeListeners = activeListeners;
        }
    }
}
