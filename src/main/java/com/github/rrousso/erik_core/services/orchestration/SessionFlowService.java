package com.github.rrousso.erik_core.services.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.Flag;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionContext;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.repositories.PersonaRepository;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRecordRepository;
import com.github.rrousso.erik_core.services.llm.FlagDetectorService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.services.session.SessionAssemblerService;
import com.github.rrousso.erik_core.services.session.SynopsisGeneratorService;
import com.github.rrousso.erik_core.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_core.services.stanza.StanzaInitializationService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

import java.util.Objects;

/**
 * Spring service that orchestrates the flow between Erik (void mode) and Narrator (stanza mode)
 * Uses simple call() with complete system prompts instead of callWithHistory()
 */
@Service
public class SessionFlowService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionFlowService.class);
       
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final SynopsisGeneratorService synopsisGenerator;
    private final FlagDetectorService flagDetector;
    private final PersonaRepository personaRepository;
    private final SessionAssemblerService sessionAssembler;
    private final StanzaInitializationService initializationService;
    private final StanzaPersistenceService persistenceService; 
    private final StanzaExtractionService extractionService;
    private String message = "no message";
    
    public SessionFlowService(
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            SynopsisGeneratorService synopsisGenerator, 
            FlagDetectorService flagDetector,
            SessionAssemblerService sessionAssembler, 
            PersonaRepository personaRepository, 
            StanzaRecordRepository stanzaRecordRepository,
            StanzaInitializationService initializationService,
            StanzaPersistenceService persistenceService,
            StanzaExtractionService extractionService) { 
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.synopsisGenerator = synopsisGenerator;
        this.flagDetector = flagDetector;
        this.sessionAssembler = sessionAssembler; 
        this.personaRepository = personaRepository;
        this.initializationService = initializationService;
        this.persistenceService = persistenceService; 
        this.extractionService = extractionService; 
        
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
        Flag flag = flagDetector.detect(userInput, state);
        
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
            message = "\n[Erik] " + callErik(state, userInput);
            
        } catch (Exception e) {
            log.error("Error in void mode", e);
            handleError(e);
            message = "\n[System] An error occurred. Please try again.\n";
        }
    }
    
    String callErik(SessionState state, String userInput) throws Exception {
         
        SessionContext context = sessionAssembler.assembleForVoid(state);
        
        String systemPrompt = promptBuilder.buildVoidPromptFromContext(context);
        
        String response = llmClient.call(
            ModelType.NARRATIVE, 
            systemPrompt, 
            userInput
        );
        
        state.getVoidHistory().addUserMessage(userInput);
        state.getVoidHistory().addAssistantMessage(response);
        
        return response;
    }

    // ========== STANZA MODE ==========

    
    void handleStanza(String userInput, SessionState state) {
        try {
            String narration = callNarrator(state, userInput);
            message = "\n[Narration] " + narration;
            
            Long stanzaId = state.getActiveStanzaId();
            
            // Increment exchange counter in DB after each exchange
            if (stanzaId != null) {
                try {
                    persistenceService.incrementExchange(stanzaId);
                } catch (Exception e) {
                    log.warn("Failed to increment exchange counter", e);
                }
                
                // NEW: Extract and update state from the narrative exchange
                try {
                    extractionService.extractAndUpdate(stanzaId, userInput, narration);
                } catch (Exception e) {
                    log.warn("Failed to extract state changes", e);
                    // Don't fail the whole exchange if extraction fails
                }
            }
            
        } catch (Exception e) {
            log.error("Error in stanza mode", e);
            handleError(e);
            message = "\n[System] An error occurred. Please try again.\n";
        }
    }
    
    String callNarrator(SessionState state, String userInput) throws Exception {
        
        SessionContext context = sessionAssembler.assembleForStanza(state);
        
        String systemPrompt = promptBuilder.buildStanzaPromptFromContext(context);
        
        String response = llmClient.call(
            ModelType.NARRATIVE, 
            systemPrompt, 
            userInput
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
            
            state.setCompletedStanza(null);
            
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            String erikResponse = callErik(state, userInput);
            builder.append("\n[Erik] " + erikResponse);
            builder.append("\n\n");
            
            log.debug("Extracting stanza details...");

            // Initialize stanza from planning conversation
            InitializedStanza initialized = initializationService.initializeFromPlanning(state.getVoidHistory());
            state.setInitializedStanza(initialized);
            
            // NEW: Persist to database and store the ID
            try {
                Persona persona = getCurrentPersona();
                Stanza savedStanza = persistenceService.saveInitializedStanza(initialized, persona);
                
                Long stanzaId = savedStanza.getId();
                if (stanzaId == null) {
                    log.error("Stanza was saved but ID is null - this shouldn't happen");
                } else {
                    state.setActiveStanzaId(stanzaId);
                    log.info("Stanza persisted to database with ID: {}", stanzaId);
                }
            } catch (Exception e) {
                log.error("Failed to persist stanza to database - continuing without persistence", e);
                // Don't fail the whole stanza start, just log the error
            }
            
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
            state.setActiveStanzaId(null);  // NEW: Clear stanza ID on failure
        }
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
            
            Long stanzaId = state.getActiveStanzaId();
            
            // NEW: Update status in database
            if (stanzaId != null) {
                try {
                    persistenceService.updateStatus(stanzaId, "paused");
                } catch (Exception e) {
                    log.warn("Failed to update stanza status in database", e);
                }
            }
                
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
                userInput + " ((Bring the scene to a natural closing moment. NARRATION ONLY - no meta-commentary, no epilogue notes, no analysis. Just the final narrative moment.))");
            builder.append("\n[Narration - Closing] ");
            builder.append(closure);
        
            state.enterVoidMode();
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
            state.setStanzaStatus(StanzaStatus.COMPLETED);   
            
            Long stanzaId = state.getActiveStanzaId();
            if (stanzaId == null) {
                log.error("Stanza was saved but ID is null - this shouldn't happen");
            } else {
                state.setActiveStanzaId(stanzaId);
                log.info("Stanza persisted to database with ID: {}", stanzaId);
            }
            
            // NEW: Update database with final status and synopsis
            if (stanzaId != null) {
                try {
                    persistenceService.updateStatus(stanzaId, "completed");
                    persistenceService.setQuickSynopsis(stanzaId, completed.getQuickSynopsis());
                    log.info("Stanza completion saved to database");
                } catch (Exception e) {
                    log.warn("Failed to update completed stanza in database", e);
                }
            }
            
            builder.append("\n\n[STANZA END]\n");
            builder.append("\n[System] Here's the quick synopsis:\n");
            builder.append(completed.getQuickSynopsis());
            builder.append("\n");
            
            try {
                String erikReflection = callErik(state, 
                    "That was a meaningful stanza to experience together.");
                builder.append("\n[Erik] ");
                builder.append(erikReflection);
                builder.append("\n");
            } catch (Exception e) {
                log.warn("Failed to get Erik's reflection on stanza end", e);
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
            
            Long stanzaId = state.getActiveStanzaId();
            
            // NEW: Update database with abandoned status
            if (stanzaId != null) {
                try {
                    persistenceService.updateStatus(stanzaId, "abandoned");
                } catch (Exception e) {
                    log.warn("Failed to update abandoned stanza in database", e);
                }
            }
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
           
            String response = callErik(state, userInput);
            builder.append("\n[STANZA ABANDONED]\n");
            builder.append("\n[Erik] ");
            builder.append(response);
            builder.append("\n");
            
            state.setInitializedStanza(null);
            state.setActiveStanzaId(null);  // NEW: Clear stanza ID
            
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
        
        CompletedStanza completed = new CompletedStanza(
            quickSynopsis, 
            state.getInitializedStanza()
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
        if (state.getInitializedStanza() == null) {
            message = "[System] No stanza to continue. Use 'start stanza' to begin a new one.\n";
            log.warn("Attempt to continue stanza with no current stanza");
            return;
        }

        log.info("Continuing paused stanza");
        state.enterStanzaMode();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        
        Long stanzaId = state.getActiveStanzaId();
        
        // NEW: Update status in database
        if (stanzaId != null) {
            try {
                persistenceService.updateStatus(stanzaId, "active");
            } catch (Exception e) {
                log.warn("Failed to update stanza status in database", e);
            }
        }
        
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

    // ========== HELPER METHODS ==========
    
    /**
     * Get the current persona from the database.
     * For now, returns the first persona (single-user system).
     * TODO: Support multiple personas later.
     */
    private Persona getCurrentPersona() {
        return personaRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No persona found in database"));
    }

    // ========== ERROR HANDLING ==========

    void handleError(Exception e) {
        log.error("Error occurred", e);
        e.printStackTrace();
    }
}