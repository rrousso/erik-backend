package com.github.rrousso.erik_core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.Entities.CompletedStanza;
import com.github.rrousso.erik_core.Entities.Flag;
import com.github.rrousso.erik_core.Entities.ModelType;
import com.github.rrousso.erik_core.Entities.SessionState;
import com.github.rrousso.erik_core.Entities.StanzaSetup;
import com.github.rrousso.erik_core.Entities.StanzaStatus;

import java.util.Objects;

/**
 * Spring service that orchestrates the flow between Erik (void mode) and Narrator (stanza mode)
 */
@Service
public class SessionFlowService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionFlowService.class);
	   
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final StanzaExtractorService stanzaExtractor;
    private final SynopsisGeneratorService synopsisGenerator;
    private final FlagDetectorService flagDetector;
    private String message = "no message";
    
    public SessionFlowService(
    		ConfigService configService,
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            StanzaExtractorService stanzaExtractor,
            SynopsisGeneratorService synopsisGenerator, 
            FlagDetectorService flagDetector) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.stanzaExtractor = stanzaExtractor;
        this.synopsisGenerator = synopsisGenerator;
        this.flagDetector = flagDetector;
        
        log.info("SessionFlowService initialized");
    }
    
    // ========== CORE ROUTING WITH FLAG DETECTION ==========

    public String handleUserInput(String userInput, SessionState state) {
        // Input validation
        Objects.requireNonNull(userInput, "userInput cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        
        if (userInput.isBlank()) {
            log.warn("Empty user input received");
            return "";
        }
        
    	// First: Detect if this is a command using analytical model
        Flag flag = flagDetector.detect(userInput, state.getStanzaStatus());
        
        log.debug("Flag detection result: {}", flag);
        
        // If flag detected, handle it directly
        if (flag != Flag.NONE) {
            handleFlag(flag, userInput, state);
            return message;
        }
        
        // No flag: route to appropriate mode for conversation/narration
        if (state.isInVoidMode()) {
            handleVoid(userInput, state);
        } else {
            handleStanza(userInput, state);
        }
        
        return message;
    }
    
    /**
     * Handle detected flags
     */
    void handleFlag(Flag flag, String userInput, SessionState state) {
        log.info("Handling flag: {}", flag);
        
        switch (flag) {
            case START_STANZA:
                 startStanza(userInput, state);
                break;
                
            case PAUSE_STANZA:
                pauseStanza(userInput, state);
                break;
                
            case CONTINUE_STANZA:
                continueStanza(userInput, state);
                break;
                
            case END_STANZA:
                endStanza(userInput, state);
                break;
                
            case ABANDON_STANZA:
                abandonStanza(state, userInput);
                break;
                
            default:
                log.error("Unexpected flag: {}", flag);
        }
    }

    // ========== VOID MODE ==========

    void handleVoid(String userInput, SessionState state) {
        try {
            message = callErik(state, userInput);
            
        } catch (Exception e) {
            log.error("Error in void mode", e);
            handleError(e);
            message = "\n[System] An error occurred. Please try again.\n";
        }
    }
    
    String callErik(SessionState state, String userInput) throws Exception {
        
        
        String systemPrompt = promptBuilder.buildVoidPrompt(state);
        
        // Use narrative model for Erik
        String response = llmClient.callWithHistory(
            ModelType.NARRATIVE, 
            systemPrompt, 
            "userInput", 
            state.getVoidHistory().getMessagesForAPI()
        );
        state.getVoidHistory().addUserMessage(userInput);
        state.getVoidHistory().addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.getVoidHistory());
        } catch (Exception e) {
            log.warn("Failed to generate synopsis", e);
        }
        
        return response;
    }

    // ========== STANZA MODE ==========

    void startStanza(String userInput, SessionState state) {
        if (state.isInStanzaMode()) {
        	message = "[System] Already in stanza mode.\n";
            log.warn("Attempt to start stanza while already in stanza mode");
            return;
        }
        if (state.getStanzaStatus() == StanzaStatus.COMPLETED) {
            message = "\n[System] You've already completed a stanza this session.\n"
            		+ " [System] Take some time to reflect and process the experience. "
            		+ "\n[System] Use 'exit' when you're ready to close the app.\n";
            log.info("Attempt to start stanza after completion");
            return;
        }
        
        log.info("Starting new stanza");
        StringBuilder builder = new StringBuilder();
        
        try {
        	
        	state.setStanzaStatus(StanzaStatus.ACTIVE);
	        String erikResponse = callErik(state, userInput);
	        builder.append(erikResponse);
	        builder.append("\n\n");
	        
	        log.debug("Extracting stanza details...");
        
            StanzaSetup setup = stanzaExtractor.extract(state.getVoidHistory());
            state.setCurrentStanza(setup);
            state.getVoidHistory().clearHistory();
            state.enterStanzaMode();
            
            // System message
            builder.append("[STANZA START]\n");
            
            String opening = callNarrator(state, "Begin the scene.");
            builder.append("\n[Opening Narration] ");
            builder.append(opening);
            
            message = builder.toString();
            log.info("Stanza started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start stanza", e);
            handleError(e);
            message = "[System] Failed to start stanza. Remaining in void mode.\n";
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.NONE);
        }
    }
    
    void handleStanza(String userInput, SessionState state) {
        try {
            String narration = callNarrator(state, userInput);
            message = "\n[Narration] " + narration;
            
        } catch (Exception e) {
            log.error("Error in stanza mode", e);
            handleError(e);
            message = "\n[System] An error occurred. Please try again.\n";
        }
    }
    
    String callNarrator(SessionState state, String userInput) throws Exception {
        
        String systemPrompt = promptBuilder.buildStanzaPrompt(state.getCurrentStanza());
        
        // Use narrative model for narrator
        String response = llmClient.callWithHistory(
            ModelType.NARRATIVE, 
            systemPrompt, 
            userInput, 
            state.getStanzaHistory().getMessagesForAPI()
        );
        state.getStanzaHistory().addUserMessage(userInput);
        state.getStanzaHistory().addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            log.warn("Failed to generate synopsis", e);
        }
        
        return response;
    }
    
    void pauseStanza(String pauseMessage, SessionState state) {
        if (state.isInVoidMode()) {
            message = "[System] Already in void mode.\n";
            log.warn("Attempt to pause stanza while already in void mode");
            return;
        }
        
        log.info("Pausing stanza");
        
        try {
	        state.enterVoidMode();
	        state.setStanzaStatus(StanzaStatus.PAUSED);
	            
	        String response = callErik(state, pauseMessage);
	        message = "\n[Erik] " + response + "\n";
            log.info("Stanza paused successfully");
            
        } catch (Exception e) {
            log.error("Failed to pause stanza", e);
            handleError(e);
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            message = "\n[System] Failed to pause stanza.\n";
        }
    }
    
    void endStanza(String userInput, SessionState state) {
        log.info("Ending stanza");
        StringBuilder builder = new StringBuilder();

        try {
            // Get closing narration with user's actual input
            String closure = callNarrator(state, 
                userInput + " ((Provide a gentle closing or resolution for this scene, bringing it to a natural end point.))");
            builder.append("\n[Narration - Closing] ");
            builder.append(closure);
        
            state.enterVoidMode();
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
            state.setStanzaStatus(StanzaStatus.COMPLETED);
            
            builder.append("\n\n[STANZA END]\n");
            builder.append("\n[System] Here's the quick synopsis:\n");
            builder.append(completed.getQuickSynopsis());
            builder.append("\n");
            
            // OPTIONAL: Add Erik's reflection (with better prompt)
            // If you want Erik to respond, use a more natural prompt:
            try {
                String erikReflection = callErik(state, 
                    "That was a meaningful stanza to experience together.");
                builder.append("\n[Erik] ");
                builder.append(erikReflection);
                builder.append("\n");
            } catch (Exception e) {
                log.warn("Failed to get Erik's reflection on stanza end", e);
                // Continue without Erik's comment
            }
            
            message = builder.toString();
            log.info("Stanza ended successfully");
            
        } catch (Exception e) {
            log.error("Failed to end stanza", e);
            handleError(e);
            message = "[System] Failed to end stanza.\n";
            state.enterStanzaMode();
        }
    }
    
    private void abandonStanza(SessionState state, String userInput) {
        if (state.isInVoidMode()) {
        	message = "[System] No active stanza to abandon.\n";
            log.warn("Attempt to abandon stanza while in void mode");
            return;
        }
        
        log.info("Abandoning stanza");
        StringBuilder builder = new StringBuilder();
        
        try {
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.ABANDONED);
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
           
            String response = callErik(state, userInput);
            builder.append("\n[STANZA ABANDONED]\n");
            builder.append("\n[Erik] ");
            builder.append(response);
            builder.append("\n");
            
            state.setCurrentStanza(null);
            state.setCompletedStanza(null);
            state.setStanzaStatus(StanzaStatus.NONE);
            
            message = builder.toString();
            log.info("Stanza abandoned successfully");
            
        } catch (Exception e) {
            log.error("Failed to abandon stanza", e);
            handleError(e);
            message = "\n[System] Failed to abandon stanza.\n";
        }
    }

    private CompletedStanza createCompletedStanza(SessionState state) {
        String quickSynopsis = "";
        try {
            quickSynopsis = synopsisGenerator.generateQuickSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            log.error("Failed to generate quick synopsis", e);
            handleError(e);
        }
        
        String detailedSynopsis = "";
        try {
            detailedSynopsis = synopsisGenerator.generateDetailedSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            log.error("Failed to generate detailed synopsis", e);
            handleError(e);
        }
        
        CompletedStanza completed = new CompletedStanza(
            quickSynopsis, 
            detailedSynopsis, 
            state.getCurrentStanza()
        );
        state.getStanzaHistory().clearHistory();
        return completed;
    }
    
    void continueStanza(String userInput, SessionState state) {
        if (state.isInStanzaMode()) {
            message = "[System] Already in stanza mode.\n";
            log.warn("Attempt to continue stanza while already in stanza mode");
            return;
        }
        if (state.getCurrentStanza() == null) {
        	message = "[System] No stanza to continue. Use 'start stanza' to begin a new one.\n";
            log.warn("Attempt to continue stanza with no current stanza");
            return;
        }

        log.info("Continuing paused stanza");
        state.enterStanzaMode();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        try {
            String pauseChanges = synopsisGenerator.generatePauseChanges(state.getVoidHistory());
            String continuation = callNarrator(state, 
                "Continue the scene implementing the following changes: " + pauseChanges);
            
            message = "\n[Narration] " + continuation;
            log.info("Stanza continued successfully");
            
        } catch (Exception e) {
            log.error("Failed to continue stanza", e);
            handleError(e);
            message = "[System] Failed to continue stanza.\n";
            state.enterVoidMode();
        }
    }

    // ========== ERROR HANDLING ==========

    void handleError(Exception e) {
        log.error("Error occurred", e);
        // Error is already logged, just print stack trace to console for debugging
        e.printStackTrace();
    }
}