package com.github.rrousso.erik_core.conversation;

import com.github.rrousso.erik_core.config.ConfigService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages conversation history for both VOID and STANZA modes with rolling synopsis.
 * Spring-managed component with prototype scope for multiple instances if needed.
 */
@Component
public class ConversationHistory {
    
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
    
    private final ConfigService configService;
    
    // Full conversation history (never cleared during session)
    private final List<Message> fullHistory = new ArrayList<>();
    
    // Current mode's history (what actually gets sent to API)
    private List<Message> currentModeHistory = new ArrayList<>();
    
    // Rolling synopsis 
    private String synopsis = "";
    private int exchangesSinceLastSynopsis = 0;
    
    // Track current mode for synopsis management
    private boolean inStanzaMode = false;
    
    public ConversationHistory(ConfigService configService) {
        this.configService = configService;
    }
    
    private int getWindowSize() {
        return configService.getWindowSize();
    }
    
    private int getSynopsisThreshold() {
        return configService.getThresholdSize();
    }
    
    public void addUserMessage(String content) {
        Message msg = new Message("user", content);
        fullHistory.add(msg);
        currentModeHistory.add(msg);
    }
    
    public void addAssistantMessage(String content) {
        Message msg = new Message("assistant", content);
        fullHistory.add(msg);
        currentModeHistory.add(msg);
        
        exchangesSinceLastSynopsis++;

    }
    
    public List<Message> getMessagesForAPI() {
        List<Message> messages = new ArrayList<>();
        
        if (!synopsis.isEmpty()) {
            messages.add(new Message("system", "PREVIOUS CONTEXT:\n" + synopsis));
        }
        
        messages.addAll(getRecentMessages(getWindowSize()));
        
        return messages;
    }
    
    public boolean shouldGenerateSynopsis() {
        int threshold = getSynopsisThreshold();
        return exchangesSinceLastSynopsis >= threshold;
    }
    
    public List<Message> getExchangesForSynopsis() {
        int windowSize = getWindowSize();
        int historySize = currentModeHistory.size();
        int endIdx = Math.max(0, historySize - (windowSize * 2));
        return new ArrayList<>(currentModeHistory.subList(0, endIdx));
    }
    
    public void updateSynopsis(String newSynopsis) {
    	synopsis = newSynopsis;
        exchangesSinceLastSynopsis = 0;
        trimCondensedMessages();
    }
    
    private void trimCondensedMessages() {
        int windowSize = getWindowSize();
        int historySize = currentModeHistory.size();
        int startIdx = Math.max(0, historySize - (windowSize * 2));
        currentModeHistory = new ArrayList<>(currentModeHistory.subList(startIdx, historySize));
    }
    
    public String getSynopsis() {
        return synopsis;
    }
    
    private List<Message> getRecentMessages(int count) {
        int size = currentModeHistory.size();
        if (size <= count) {
            return new ArrayList<>(currentModeHistory);
        }
        return new ArrayList<>(currentModeHistory.subList(size - count, size));
    }
    
    public void enterStanzaMode() {
        currentModeHistory.clear();
        inStanzaMode = true;
        synopsis = "";
        exchangesSinceLastSynopsis = 0;
    }
    
    
    public List<Message> getConversationForExtraction() {
        return new ArrayList<>(currentModeHistory);
    }
    
    public void printDebugInfo() {
        System.out.println("[DEBUG] Full history size: " + fullHistory.size());
        System.out.println("[DEBUG] Current mode history size: " + currentModeHistory.size());
        System.out.println("[DEBUG] In stanza mode: " + inStanzaMode);
        System.out.println("[DEBUG] Stanza synopsis length: " + synopsis.length());
        System.out.println("[DEBUG] Exchanges since last synopsis: " + exchangesSinceLastSynopsis);
    }
    
    public void clear() {
        fullHistory.clear();
        currentModeHistory.clear();
        synopsis = "";
        exchangesSinceLastSynopsis = 0;
    }

	public void clearHistory() {
		currentModeHistory.clear();
	    synopsis = "";
	    exchangesSinceLastSynopsis = 0;
	    System.out.println("[ConversationHistory] History cleared");
		
	}
}
