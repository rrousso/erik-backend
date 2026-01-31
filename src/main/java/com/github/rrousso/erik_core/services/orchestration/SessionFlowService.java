package com.github.rrousso.erik_core.services.orchestration;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.Flag;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.services.llm.FlagDetectorService;
import com.github.rrousso.erik_core.services.orchestration.strategies.FlowStrategyFactory;

/**
 * Main orchestrator for the Erik application flow.
 * 
 * This service has ONE job: route user input to the appropriate strategy.
 * 
 * BEFORE refactoring: 500+ lines with complex switch/if-else logic
 * AFTER refactoring: ~50 lines with clean delegation to strategies
 * 
 * Flow:
 * 1. Validate input
 * 2. Detect if input contains a flag (START, PAUSE, END, etc.)
 * 3. Get appropriate strategy from factory
 * 4. Execute strategy and return result
 * 
 * All business logic is now in:
 * - FlowStrategy implementations (strategies package)
 * - ConversationService (for LLM conversations)
 * - StanzaCompletionService (for stanza completion)
 * 
 * This follows the Single Responsibility Principle and Strategy Pattern.
 */
@Service
public class SessionFlowService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionFlowService.class);
       
    private final FlowStrategyFactory flowStrategyFactory;
    private final FlagDetectorService flagDetector;
    
    public SessionFlowService(
            FlowStrategyFactory flowStrategyFactory, 
            FlagDetectorService flagDetector) { 
        this.flowStrategyFactory = flowStrategyFactory;
        this.flagDetector = flagDetector;
        
        log.info("SessionFlowService initialized with Strategy pattern");
    }

    /**
     * Handle user input and return the appropriate response.
     * 
     * This is the main entry point for all user interactions.
     * 
     * @param userInput The user's text input
     * @param state The current session state
     * @return The response message to display to the user
     */
    public String handleUserInput(String userInput, SessionState state) {
        // Input validation
        Objects.requireNonNull(userInput, "userInput cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        
        if (userInput.isBlank()) {
            log.warn("Empty user input received");
            return "";
        }
        
        // Detect if this is a command (START, PAUSE, END, etc.)
        Flag flag = flagDetector.detect(userInput, state);
        
        // Route to appropriate strategy
        if (flag != Flag.NONE) {
            log.debug("Flag detected: {} - routing to flag strategy", flag);
            return flowStrategyFactory.getStrategyForFlag(flag).execute(userInput, state);
        }
        
        // No flag - route based on current mode (VOID or STANZA)
        log.debug("No flag detected - routing to mode-based strategy");
        return flowStrategyFactory.getStrategyForConversation(state).execute(userInput, state);
    }
}