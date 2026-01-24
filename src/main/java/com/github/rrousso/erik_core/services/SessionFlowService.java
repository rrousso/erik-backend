package com.github.rrousso.erik_core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.entities.CompletedStanza;
import com.github.rrousso.erik_core.entities.Flag;
import com.github.rrousso.erik_core.entities.ModelType;
import com.github.rrousso.erik_core.entities.Persona;
import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.entities.StanzaRecord;
import com.github.rrousso.erik_core.entities.StanzaMetadata;
import com.github.rrousso.erik_core.entities.StanzaStatus;
import com.github.rrousso.erik_core.repositories.PersonaRepository;
import com.github.rrousso.erik_core.repositories.StanzaRecordRepository;
import com.github.rrousso.erik_core.entities.SessionContext;

import jakarta.transaction.Transactional;

import java.util.ArrayList;
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
    private final StanzaExtractorService stanzaExtractor;
    private final SynopsisGeneratorService synopsisGenerator;
    private final FlagDetectorService flagDetector;
    private final PersonaRepository personaRepository;
    private final StanzaRecordRepository stanzaRecordRepository;
    private final SessionAssemblerService sessionAssembler;
    private String message = "no message";
    
    public SessionFlowService(
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            StanzaExtractorService stanzaExtractor,
            SynopsisGeneratorService synopsisGenerator, 
            FlagDetectorService flagDetector,
            SessionAssemblerService sessionAssembler,  // <-- ADD THIS
            PersonaRepository personaRepository, 
            StanzaRecordRepository stanzaRecordRepository) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.stanzaExtractor = stanzaExtractor;
        this.synopsisGenerator = synopsisGenerator;
        this.flagDetector = flagDetector;
        this.sessionAssembler = sessionAssembler; 
        this.personaRepository = personaRepository;
        this.stanzaRecordRepository = stanzaRecordRepository;
        
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
        
	        StanzaMetadata setup;

	        if (state.hasLoadedStanzaMemory()) {
	            log.info("Starting stanza from loaded memory");
	            setup = convertRecordToMetadata(state.getLoadedStanzaMemory());
	            
	            String changes = synopsisGenerator.generatePauseChanges(state.getVoidHistory());
	            if (changes != null && !changes.isBlank()) {
	                setup.getSpecialRules().add("USER REQUESTED CHANGES: " + changes);
	            }
	            
	            state.setLoadedStanzaMemory(null);
	        } else {
	            setup = stanzaExtractor.extractFromVoidHistory(state.getVoidHistory());
	        }

	        state.setCurrentStanza(setup);
            
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
                userInput + " ((Bring the scene to a natural closing moment. NARRATION ONLY - no meta-commentary, no epilogue notes, no analysis. Just the final narrative moment.))");
            builder.append("\n[Narration - Closing] ");
            builder.append(closure);
        
            state.enterVoidMode();
            
            StanzaMetadata setup = stanzaExtractor.extractFromStanzaHistory(state.getStanzaHistory());
            state.setCurrentStanza(setup);
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
            state.setStanzaStatus(StanzaStatus.COMPLETED);          
            
            saveStanzaToDb(completed,state.getCurrentStanza());
            
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
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
           
            String response = callErik(state, userInput);
            builder.append("\n[STANZA ABANDONED]\n");
            builder.append("\n[Erik] ");
            builder.append(response);
            builder.append("\n");
            
            state.setCurrentStanza(null);
            
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
    
    private StanzaMetadata convertRecordToMetadata(StanzaRecord record) {
        StanzaMetadata metadata = new StanzaMetadata();
        metadata.setSetting(record.getSetting() != null ? record.getSetting() : "");
        metadata.setPremise(record.getPremise() != null ? record.getPremise() : "");
        metadata.setUserRole(record.getUserRole() != null ? record.getUserRole() : "");
        metadata.setUserBackstory(record.getUserBackStory() != null ? record.getUserBackStory() : "");
        metadata.setTone(record.getTone() != null ? record.getTone() : "");
        metadata.setCharacters(record.getCharacters() != null ? new ArrayList<>(record.getCharacters()) : new ArrayList<>());
        metadata.setSpecialRules(record.getSpecialRules() != null ? new ArrayList<>(record.getSpecialRules()) : new ArrayList<>());
        metadata.setPreviousEvents(record.getPreviousEvents() != null ? new ArrayList<>(record.getPreviousEvents()) : new ArrayList<>());
        return metadata;
    }
    
    // ========== DB HANDLING ==========
    
    @Transactional
    private void saveStanzaToDb(CompletedStanza completed, StanzaMetadata stanzaMetadata) {
    	try {
			Persona personaEntity = personaRepository.findAll().get(0);
			
			StanzaRecord stanzaRecordEntity = new StanzaRecord(personaEntity, completed.getQuickSynopsis());
			stanzaRecordEntity.setPremise(stanzaMetadata.getPremise());
			stanzaRecordEntity.setSetting(stanzaMetadata.getSetting());
			stanzaRecordEntity.setTone(stanzaMetadata.getTone());
			stanzaRecordEntity.setUserRole(stanzaMetadata.getUserRole());
			stanzaRecordEntity.setUserBackstory(stanzaMetadata.getUserBackstory());
			stanzaRecordEntity.setCharacters(stanzaMetadata.getCharacters());
			stanzaRecordEntity.setPreviousEvents(stanzaMetadata.getPreviousEvents());
			stanzaRecordEntity.setSpecialRules(stanzaMetadata.getSpecialRules());
			stanzaRecordRepository.save(stanzaRecordEntity);
			
			log.info("Stanza saved to database successfully");
		} catch (Exception e) {
			log.error("Failed to save stanza to database", e);
		}	
	}
    

    // ========== ERROR HANDLING ==========

    void handleError(Exception e) {
        log.error("Error occurred", e);
        e.printStackTrace();
    }
}