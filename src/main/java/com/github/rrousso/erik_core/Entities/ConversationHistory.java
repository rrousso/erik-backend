package com.github.rrousso.erik_core.entities;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages conversation history for any mode with rolling synopsis.
 * fullHistory is kept ONLY for logging/debugging, not for synopsis generation.
 * Synopsis generation uses: synopsis + currentHistory (the rolling window).
 */
public class ConversationHistory {
	  private static final Logger log = LoggerFactory.getLogger(ConversationHistory.class);
    
	  	public static class Message {
	        private final String role;      // "user" or "assistant"
	        private final String content;
	        
	        public Message(String role, String content) {
	            this.role = role;
	            this.content = content;
	    }
        
        public String getRole() {
            return role;
        }
        
        public String getContent() {
            return content;
        }
    }
    
    // Full conversation history (ONLY for logging/debugging, never used for synopsis)
    private final List<Message> fullHistory = new ArrayList<>();
    
    // Current mode's history (gets trimmed for context management)
    private List<Message> currentHistory = new ArrayList<>();
    
    // Rolling synopsis 
    private String synopsis = "";
    
    public ConversationHistory() {      
    }    
    
    public void addUserMessage(String content) {
        Message msg = new Message("user", content);
        fullHistory.add(msg);
        currentHistory.add(msg);
        log.info("[ConversationHistory] Added user message. CurrentMode size: " + currentHistory.size() +
            ", Full history size: " + fullHistory.size());
    }

    public void addAssistantMessage(String content) {
        Message msg = new Message("assistant", content);
        fullHistory.add(msg);
        currentHistory.add(msg);

        log.info("[ConversationHistory] Added assistant message. CurrentMode size: " + currentHistory.size() +
            ", Full history size: " + fullHistory.size());

    }
    
    /**
     * Get recent messages formatted as TEXT for system prompt inclusion
     * Format: "USER: content\n\nASSISTANT: content\n\n"
     */
    public String getRecentExchangesForSystemPrompt() {
        if (currentHistory.isEmpty()) {
            return "";
        }
        
        StringBuilder exchanges = new StringBuilder();
        for (Message msg : currentHistory) {
            exchanges.append(msg.getRole().toUpperCase())
                     .append(": ")
                     .append(msg.getContent())
                     .append("\n\n");
        }
        
        log.info("[ConversationHistory] Formatted " + currentHistory.size() + 
                " messages as text for system prompt (" + exchanges.length() + " chars)");
        
        return exchanges.toString();
    }
    
    /**
     * Get synopsis (raw text, no formatting)
     */
    public String getSynopsis() {
        return synopsis;
    }
    
    /**
     * Get current (trimmed) messages
     * For synopsis generation, use this + getSynopsis() to get complete context
     */
    public List<Message> getAllMessages() {
        return new ArrayList<>(currentHistory);
    }
    
    public void updateSynopsis(String newSynopsis, int window) {
        int oldHistorySize = currentHistory.size();
        String oldSynopsis = synopsis;

        log.info("[ConversationHistory] Old synopsis length: " + oldSynopsis.length());
        log.info("[ConversationHistory] New synopsis length: " + newSynopsis.length());
        log.info("[ConversationHistory] History size before trim: " + oldHistorySize);

        synopsis = newSynopsis;
        trimCondensedMessages(window);

        log.info("[ConversationHistory] Messages trimmed: " + (oldHistorySize - currentHistory.size()));
        log.info("[ConversationHistory] Full history size (logging only): " + fullHistory.size());
    }

    private void trimCondensedMessages(int windowSize) {
        int historySize = currentHistory.size();
        int keepCount = windowSize;  // Keep this many recent messages
        
        if (historySize > keepCount) {
            int startIdx = historySize - keepCount;
            
            log.info("[ConversationHistory] trimCondensedMessages - Window size: " + windowSize +
                ", Keep count: " + keepCount +
                ", History size: " + historySize +
                ", Start index: " + startIdx +
                ", Will keep: " + keepCount + " most recent messages");
            
            currentHistory = new ArrayList<>(currentHistory.subList(startIdx, historySize));
        } else {
            log.info("[ConversationHistory] trimCondensedMessages - Window size: " + windowSize +
                ", Keep count: " + keepCount +
                ", History size: " + historySize +
                ", No trimming needed (history smaller than keep count)");
        }
    }
 
    /**
     * Get messages that should be condensed into synopsis (OLD messages beyond window)
     */
    public List<Message> getExchangesForSynopsis(int windowSize) {
        int historySize = currentHistory.size();
        int keepCount = windowSize;  // We keep this many recent messages
        
        // Calculate how many OLD messages exist (beyond the keep window)
        int oldMessagesCount = historySize - keepCount;
        
        log.info("[ConversationHistory] getExchangesForSynopsis - Window size: " + windowSize +
            ", Keep count: " + keepCount +
            ", History size: " + historySize +
            ", Old messages to condense: " + Math.max(0, oldMessagesCount));

        // If there are no old messages beyond the window, nothing to condense
        if (oldMessagesCount <= 0) {
            return new ArrayList<>();
        }

        // Return the old messages (from start to end of old messages)
        return new ArrayList<>(currentHistory.subList(0, oldMessagesCount));
    }
    
    /**
     * Get conversation for extraction (planning -> stanza setup)
     */
    public List<Message> getConversationForExtraction() {
        return new ArrayList<>(currentHistory);
    }

	public void clearHistory() {
		currentHistory.clear();
	    synopsis = "";
	    log.info("[ConversationHistory] Current history and synopsis cleared");
	    log.info("[ConversationHistory] Full history preserved for logging: " + fullHistory.size() + " messages");
	}

	public int getCurrentHistorySize() {
		return currentHistory.size();
	}
}