package com.github.rrousso.erik_core.services;

import com.github.rrousso.erik_core.Entities.Flag;
import com.github.rrousso.erik_core.Entities.ModelType;
import com.github.rrousso.erik_core.Entities.StanzaStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Service for detecting system flags from user input using a lightweight analytical model.
 * This pre-filter determines if the user is issuing a command before calling the main narrative models.
 */
@Service
public class FlagDetectorService {
    
    private static final Logger log = LoggerFactory.getLogger(FlagDetectorService.class);
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    
    public FlagDetectorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        
        log.info("FlagDetectorService initialized");
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
        // Input validation
        Objects.requireNonNull(userInput, "userInput cannot be null");
        Objects.requireNonNull(currentStatus, "currentStatus cannot be null");
        
        if (userInput.isBlank()) {
            log.warn("Empty user input provided to flag detector");
            return Flag.NONE;
        }
        
        try {
            String prompt = buildFlagDetectionPrompt(userInput, currentStatus);
            String response = llmClient.call(ModelType.ANALYTICAL, "", prompt);
            
            Flag flag = parseResponse(response.trim(), currentStatus);
            
            // Debug logging
            log.debug("Flag detection - Input: \"{}\", Status: {}, Response: \"{}\", Flag: {}", 
                userInput, currentStatus, response.trim(), flag);
  
            return flag;
        } catch (Exception e) {
            log.error("Error detecting flag from input: \"{}\"", userInput, e);
            // On error, assume no flag
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
            .replace("{STATUS}", currentStatus.name())
            .replace("{AVAILABLE_FLAGS}", availableFlags)
            .replace("{USER_INPUT}", userInput);
    }
    
    /**
     * Get available flags based on current status
     * IMPORTANT: Keep these simple and clear - just the command names
     */
    private String getAvailableFlags(StanzaStatus currentStatus) {
        return switch (currentStatus) {
            case NONE -> "START";
            case ACTIVE -> "PAUSE, END, ABANDON";
            case PAUSED -> "CONTINUE";
            case ABANDONED -> "START";
            case COMPLETED -> "NONE";
        };
    }
    
    /**
     * Parse the LLM response into a Flag
     * FIXED: Check for NONE first to avoid "NONE".contains("END") bug!
     */
    private Flag parseResponse(String response, StanzaStatus currentStatus) {
        String cleanResponse = response.toUpperCase().trim();
        
        // CRITICAL: Check for NONE first! 
        // Otherwise "NONE".contains("END") returns true
        if (cleanResponse.equals("NONE") || cleanResponse.contains("NO COMMAND")) {
            return Flag.NONE;
        }
        
        // Now check for actual commands
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
        
        // VALIDATION: Ensure the detected flag is actually valid for current status
        if (!isValidFlagForStatus(flag, currentStatus)) {
            log.warn("Detected flag {} is not valid for status {}. Returning NONE.", 
                flag, currentStatus);
            return Flag.NONE;
        }
        
        return flag;
    }
    
    /**
     * Validate that a flag is available for the current status
     */
    private boolean isValidFlagForStatus(Flag flag, StanzaStatus status) {
        return switch (status) {
            case NONE -> flag == Flag.START_STANZA || flag == Flag.NONE;
            case ACTIVE -> flag == Flag.PAUSE_STANZA || flag == Flag.END_STANZA || 
                          flag == Flag.ABANDON_STANZA || flag == Flag.NONE;
            case PAUSED -> flag == Flag.CONTINUE_STANZA || flag == Flag.NONE;
            case ABANDONED -> flag == Flag.START_STANZA || flag == Flag.NONE;
            case COMPLETED -> flag == Flag.NONE;
        };
    }
}