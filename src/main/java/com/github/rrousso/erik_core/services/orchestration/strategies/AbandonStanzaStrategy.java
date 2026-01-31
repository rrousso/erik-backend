package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.orchestration.StanzaCompletionService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the ABANDON_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're currently in stanza mode
 * 2. Transitions from STANZA mode back to VOID mode
 * 3. Updates database status to "abandoned"
 * 4. Creates a completed stanza record (with quick synopsis)
 * 5. Gets Erik's response acknowledging the abandonment
 * 
 * A new stanza can be started later with the START_STANZA flag.
 */
@Component
public class AbandonStanzaStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(AbandonStanzaStrategy.class);

    private final ConversationService conversationService;
    private final StanzaCompletionService completionService;
    private final StanzaPersistenceService persistenceService;
    
    public AbandonStanzaStrategy(
            ConversationService conversationService,
            StanzaCompletionService completionService,
            StanzaPersistenceService persistenceService) {
        this.conversationService = conversationService;
        this.completionService = completionService;
        this.persistenceService = persistenceService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        // Validation: Check if in void mode
        if (state.isInVoidMode()) {
            log.warn("Attempt to abandon stanza while in void mode");
            return "[System] No active stanza to abandon.\n";
        }
        
        log.info("Abandoning stanza");
        StringBuilder builder = new StringBuilder();
        
        try {
            // Transition to void mode
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.ABANDONED);
            
            // Update database status
            Long stanzaId = state.getActiveStanzaId();
            if (stanzaId != null) {
                try {
                    persistenceService.updateStatus(stanzaId, "abandoned");
                } catch (Exception e) {
                    log.warn("Failed to update abandoned stanza in database", e);
                }
            }
            
            // Create completed stanza record
            CompletedStanza completed = completionService.createCompletedStanza(state);
            state.setCompletedStanza(completed);
           
            // Get Erik's response
            String response = conversationService.converseWithErik(state, userInput);
            builder.append("\n[STANZA ABANDONED]\n");
            builder.append("\n[Erik] ");
            builder.append(response);
            builder.append("\n");
            
            // Clean up state
            state.setInitializedStanza(null);
            state.setActiveStanzaId(null);
            
            log.info("Stanza abandoned successfully");
            return builder.toString();
            
        } catch (Exception e) {
            log.error("Failed to abandon stanza", e);
            
            // Rollback state changes on error
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            
            return "\n[System] Failed to abandon stanza.\n";
        }
    }
}