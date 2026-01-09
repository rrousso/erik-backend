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
        private final FlagExtractor.Flag flag;
        private final RoutingMode routingMode;
        
        public FlagDetectionResult(FlagExtractor.Flag flag, RoutingMode routingMode) {
            this.flag = flag;
            this.routingMode = routingMode;
        }
        
        public FlagExtractor.Flag getFlag() {
            return flag;
        }
        
        public RoutingMode getRoutingMode() {
            return routingMode;
        }
        
        public boolean hasFlag() {
            return flag != FlagExtractor.Flag.NONE;
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
    public FlagDetectionResult detect(String userInput, StanzaStatus currentStatus) {
        try {
            
            String prompt = buildFlagDetectionPrompt(userInput, currentStatus);
            String response = llmClient.call(ModelType.ANALYTICAL, "", prompt);
            
            FlagDetectionResult result = parseResponse(response.trim(), currentStatus);
  
            return result;
        } catch (Exception e) {
            System.err.println("[FlagDetector] Error detecting flag: " + e.getMessage());
            // On error, assume no flag and route based on current status
            return new FlagDetectionResult(
                FlagExtractor.Flag.NONE,
                currentStatus == StanzaStatus.ACTIVE ? RoutingMode.STANZA : RoutingMode.VOID
            );
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
    private FlagDetectionResult parseResponse(String response, StanzaStatus currentStatus) {
        String cleanResponse = response.toUpperCase().trim();
        
        // Extract just the flag word (remove any extra text)
        FlagExtractor.Flag flag = FlagExtractor.Flag.NONE;
        
        if (cleanResponse.contains("START")) {
            flag = FlagExtractor.Flag.START_STANZA;
        } else if (cleanResponse.contains("PAUSE")) {
            flag = FlagExtractor.Flag.PAUSE_STANZA;
        } else if (cleanResponse.contains("CONTINUE")) {
            flag = FlagExtractor.Flag.CONTINUE_STANZA;
        } else if (cleanResponse.contains("END")) {
            flag = FlagExtractor.Flag.END_STANZA;
        } else if (cleanResponse.contains("ABANDON")) {
            flag = FlagExtractor.Flag.ABANDON_STANZA;
        }
        
        // Determine routing based on flag and current status
        RoutingMode routing = determineRouting(flag, currentStatus);
        
        return new FlagDetectionResult(flag, routing);
    }
    
    /**
     * Determine which mode should handle the input
     */
    private RoutingMode determineRouting(FlagExtractor.Flag flag, StanzaStatus currentStatus) {
        // If no flag, route based on current status
        if (flag == FlagExtractor.Flag.NONE) {
            return currentStatus == StanzaStatus.ACTIVE ? RoutingMode.STANZA : RoutingMode.VOID;
        }
        
        // Flags that trigger system actions directly
        return switch (flag) {
            case START_STANZA -> RoutingMode.SYSTEM;  // System handles stanza start
            case PAUSE_STANZA -> RoutingMode.SYSTEM;  // System handles pause
            case CONTINUE_STANZA -> RoutingMode.SYSTEM;  // System handles continue
            case END_STANZA -> RoutingMode.SYSTEM;  // System handles end
            case ABANDON_STANZA -> RoutingMode.SYSTEM;  // System handles abandon
            default -> currentStatus == StanzaStatus.ACTIVE ? RoutingMode.STANZA : RoutingMode.VOID;
        };
    }
}