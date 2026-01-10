package com.github.rrousso.erik_core.flags;

import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.llm.ModelType;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.stanza.StanzaStatus;
import org.springframework.stereotype.Service;

/**
 * Service for detecting system flags from user input using a lightweight analytical model.
 * This pre-filter determines if the user is issuing a command before calling the main narrative models.
 */
@Service
public class FlagDetectorService {
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    
    public FlagDetectorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }
    
    /**
     * Result of flag detection including the flag and which mode should handle it
     */
    public static class FlagDetectionResult {
        private final Flag flag;
        private final RoutingMode routingMode;
        
        public FlagDetectionResult(Flag flag, RoutingMode routingMode) {
            this.flag = flag;
            this.routingMode = routingMode;
        }
        
        public Flag getFlag() {
            return flag;
        }
        
        public RoutingMode getRoutingMode() {
            return routingMode;
        }
        
        public boolean hasFlag() {
            return flag != Flag.NONE;
        }
    }
    
    /**
     * Which mode should handle the user input
     */
    public enum RoutingMode {
        VOID,    // Erik should handle this
        STANZA,  // Narrator should handle this
        SYSTEM   // System should handle directly (e.g., flag-only commands)
    }
    
    /**
     * Detect flag from user input based on current stanza status
     */
    public Flag detect(String userInput, StanzaStatus currentStatus) {
        try {
            
            String prompt = buildFlagDetectionPrompt(userInput, currentStatus);
            String response = llmClient.call(ModelType.ANALYTICAL, "", prompt);
            
            Flag flag = parseResponse(response.trim(), currentStatus);
  
            return flag;
        } catch (Exception e) {
            System.err.println("[FlagDetector] Error detecting flag: " + e.getMessage());
            // On error, assume no flag and route based on current status
            return Flag.NONE;
        }
    }
    
    /**
     * Build the prompt for flag detection
     */
    private String buildFlagDetectionPrompt(String userInput, StanzaStatus currentStatus) {
        String template = promptBuilder.buildFlagDetectionPrompt();
        String availableFlags = getAvailableFlags(currentStatus);
        
        return template
            .replace("{STATUS}", currentStatus.getLabel())
            .replace("{AVAILABLE_FLAGS}", availableFlags)
            .replace("{USER_INPUT}", userInput);
    }
    
    /**
     * Get available flags based on current status
     */
    private String getAvailableFlags(StanzaStatus currentStatus) {
        return switch (currentStatus) {
            case NONE -> "START (to begin a new stanza)";
            case ACTIVE -> "PAUSE, END, ABANDON (during active stanza)";
            case PAUSED -> "CONTINUE (to resume stanza)";
            case ABANDONED -> "START (to begin a new stanza after abandoning previous)";
            case COMPLETED -> "NONE (stanza is completed, no commands available)";
        };
    }
    
    /**
     * Parse the LLM response into a FlagDetectionResult
     */
    private Flag parseResponse(String response, StanzaStatus currentStatus) {
        String cleanResponse = response.toUpperCase().trim();
        
        // Extract just the flag word (remove any extra text)
        Flag flag = Flag.NONE;
        
        if (cleanResponse.contains("START")) {
            flag = Flag.START_STANZA;
        } else if (cleanResponse.contains("PAUSE")) {
            flag = Flag.PAUSE_STANZA;
        } else if (cleanResponse.contains("CONTINUE")) {
            flag = Flag.CONTINUE_STANZA;
        } else if (cleanResponse.contains("END")) {
            flag = Flag.END_STANZA;
        } else if (cleanResponse.contains("ABANDON")) {
            flag = Flag.ABANDON_STANZA;
        }
        
        return flag;
    }

}