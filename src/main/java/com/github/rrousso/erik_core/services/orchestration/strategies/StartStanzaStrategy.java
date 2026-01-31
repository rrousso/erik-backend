package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.repositories.PersonaRepository;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.stanza.StanzaInitializationService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the START_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're not already in a stanza
 * 2. Gets Erik's confirmation response
 * 3. Extracts stanza setup from planning conversation
 * 4. Persists the stanza to database
 * 5. Gets opening narration from the Narrator
 * 
 * Transitions the session from VOID mode to STANZA mode.
 */
@Component
public class StartStanzaStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(StartStanzaStrategy.class);
    
    private final ConversationService conversationService;
    private final StanzaInitializationService initializationService;
    private final StanzaPersistenceService persistenceService;
    private final PersonaRepository personaRepository;
    
    public StartStanzaStrategy(
            ConversationService conversationService,
            StanzaInitializationService initializationService,
            StanzaPersistenceService persistenceService,
            PersonaRepository personaRepository) {
        this.conversationService = conversationService;
        this.initializationService = initializationService;
        this.persistenceService = persistenceService;
        this.personaRepository = personaRepository;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        // Validation: Check if already in stanza mode
        if (state.isInStanzaMode()) {
            log.warn("Attempt to start stanza while already in stanza mode");
            return "[System] Already in stanza mode.\n";
        }
        
        // Validation: Check if already completed a stanza
        if (state.getStanzaStatus() == StanzaStatus.COMPLETED) {
            log.info("Attempt to start stanza after completion");
            return "\n[System] You've already completed a stanza this session.\n"
                    + " [System] Take some time to reflect and process the experience. "
                    + "\n[System] Use 'exit' when you're ready to close the app.\n";
        }
        
        log.info("Starting new stanza");
        StringBuilder builder = new StringBuilder();

        try {
            // Clear any previous completed stanza
            state.setCompletedStanza(null);
            
            // Transition to stanza mode
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            
            // Get Erik's confirmation response
            String erikResponse = conversationService.converseWithErik(state, userInput);
            builder.append("\n[Erik] ").append(erikResponse);
            builder.append("\n\n");
            
            log.debug("Extracting stanza details from planning conversation...");

            // Initialize stanza from planning conversation
            Stanza loadedStanza = state.getLoadedStanzaMemory();
            InitializedStanza initialized = initializationService.initializeFromPlanning(
                state.getVoidHistory(), 
                loadedStanza
            );
            
            // IMPORTANT: Store initialized stanza in state
            state.setInitializedStanza(initialized);
            
            // Persist to database and store the ID
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
            
            // Get opening narration
            String opening = conversationService.converseWithNarrator(state, "Begin the scene.");
            builder.append("\n[Opening Narration] ");
            builder.append(opening);
            
            log.info("Stanza started successfully");
            return builder.toString();
            
        } catch (Exception e) {
            log.error("Failed to start stanza", e);
            
            // Rollback state changes on error
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.NONE);
            state.setActiveStanzaId(null);
            state.setInitializedStanza(null);
            
            return "[System] Failed to start stanza. Remaining in void mode.\n";
        }
    }
    
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
}