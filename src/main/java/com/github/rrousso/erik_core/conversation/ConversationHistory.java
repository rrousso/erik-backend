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
    
    // Rolling synopsis for stanza mode
    private String stanzaSynopsis = "";
    private int stanzaExchangesSinceLastSynopsis = 0;
    
    // Rolling synopsis for void mode
    private String voidSynopsis = "";
    private int voidExchangesSinceLastSynopsis = 0;
    
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
        
        if (inStanzaMode) {
            stanzaExchangesSinceLastSynopsis++;
        } else {
            voidExchangesSinceLastSynopsis++;
        }
    }
    
    public List<Message> getMessagesForAPI(boolean isStanzaMode) {
        List<Message> messages = new ArrayList<>();
        
        String synopsis = isStanzaMode ? stanzaSynopsis : voidSynopsis;
        if (!synopsis.isEmpty()) {
            messages.add(new Message("system", "PREVIOUS CONTEXT:\n" + synopsis));
        }
        
        messages.addAll(getRecentMessages(getWindowSize()));
        
        return messages;
    }
    
    public boolean shouldGenerateSynopsis(boolean isStanzaMode) {
        int threshold = getSynopsisThreshold();
        if (isStanzaMode) {
            return stanzaExchangesSinceLastSynopsis >= threshold;
        } else {
            return voidExchangesSinceLastSynopsis >= threshold;
        }
    }
    
    public List<Message> getExchangesForSynopsis(boolean isStanzaMode) {
        int windowSize = getWindowSize();
        int historySize = currentModeHistory.size();
        int endIdx = Math.max(0, historySize - (windowSize * 2));
        return new ArrayList<>(currentModeHistory.subList(0, endIdx));
    }
    
    public void updateSynopsis(String newSynopsis, boolean isStanzaMode) {
        if (isStanzaMode) {
            stanzaSynopsis = newSynopsis;
            stanzaExchangesSinceLastSynopsis = 0;
            trimCondensedMessages();
        } else {
            voidSynopsis = newSynopsis;
            voidExchangesSinceLastSynopsis = 0;
            trimCondensedMessages();
        }
    }
    
    private void trimCondensedMessages() {
        int windowSize = getWindowSize();
        int historySize = currentModeHistory.size();
        int startIdx = Math.max(0, historySize - (windowSize * 2));
        currentModeHistory = new ArrayList<>(currentModeHistory.subList(startIdx, historySize));
    }
    
    public String getSynopsis(boolean isStanzaMode) {
        return isStanzaMode ? stanzaSynopsis : voidSynopsis;
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
        stanzaSynopsis = "";
        stanzaExchangesSinceLastSynopsis = 0;
    }
    
    public void returnToVoid() {
        inStanzaMode = false;
    }
    
    public List<Message> getVoidConversationForExtraction() {
        return new ArrayList<>(currentModeHistory);
    }
    
    public void printDebugInfo() {
        System.out.println("[DEBUG] Full history size: " + fullHistory.size());
        System.out.println("[DEBUG] Current mode history size: " + currentModeHistory.size());
        System.out.println("[DEBUG] In stanza mode: " + inStanzaMode);
        if (inStanzaMode) {
            System.out.println("[DEBUG] Stanza synopsis length: " + stanzaSynopsis.length());
            System.out.println("[DEBUG] Exchanges since last synopsis: " + stanzaExchangesSinceLastSynopsis);
        } else {
            System.out.println("[DEBUG] Void synopsis length: " + voidSynopsis.length());
            System.out.println("[DEBUG] Exchanges since last synopsis: " + voidExchangesSinceLastSynopsis);
        }
    }
    
    public void clear() {
        fullHistory.clear();
        currentModeHistory.clear();
        stanzaSynopsis = "";
        voidSynopsis = "";
        stanzaExchangesSinceLastSynopsis = 0;
        voidExchangesSinceLastSynopsis = 0;
    }
}
