package com.github.rrousso.erik_core.Entities;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages conversation history for any mode with rolling synopsis.
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
    
    // Full conversation history (never cleared during session)
    private final List<Message> fullHistory = new ArrayList<>();
    
    // Current mode's history (what actually gets sent to API)
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
    
    public List<Message> getMessagesForAPI() {
        List<Message> messages = new ArrayList<>();

        if (!synopsis.isEmpty()) {
        	log.info("[ConversationHistory] getMessagesForAPI - Including synopsis in system message (" + synopsis.length() + " chars)");
            messages.add(new Message("system", "PREVIOUS CONTEXT:\n" + synopsis));
        } else {
        	log.info("[ConversationHistory] getMessagesForAPI - No synopsis available");
        }

        List<Message> recentMessages = getRecentMessages();
        messages.addAll(recentMessages);

        log.info("[ConversationHistory] getMessagesForAPI - Returning " + messages.size() +
            " messages (synopsis: " + (!synopsis.isEmpty() ? "1" : "0") +
            ", recent: " + recentMessages.size() + ")");

        return messages;
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
    }

    private void trimCondensedMessages(int windowSize) {
        int historySize = currentHistory.size();
        int keepCount = windowSize * 2;  // Keep this many recent messages
        
        // FIXED: If we have more messages than we want to keep, trim from the start
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
    
    public String getSynopsis() {
        return synopsis;
    }
    
    private List<Message> getRecentMessages() {
        return new ArrayList<>(currentHistory);
    }
 
    /**
     * Get messages that should be condensed into synopsis
     */
    public List<Message> getExchangesForSynopsis(ConversationHistory history, int windowSize) {
        int historySize = history.getCurrentHistorySize();
        int keepCount = windowSize * 2;  // We keep this many recent messages
        
        // Calculate how many OLD messages exist (beyond the keep window)
        // These are the ones that should be condensed
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
    
    
    public List<Message> getConversationForExtraction() {
        return new ArrayList<>(currentHistory);
    }
    
    
    public void clear() {
        fullHistory.clear();
        currentHistory.clear();
        synopsis = "";
    }

	public void clearHistory() {
		currentHistory.clear();
	    synopsis = "";
	    log.info("[ConversationHistory] History cleared");
		
	}

	public int getCurrentHistorySize() {
		return currentHistory.size();
	}
}