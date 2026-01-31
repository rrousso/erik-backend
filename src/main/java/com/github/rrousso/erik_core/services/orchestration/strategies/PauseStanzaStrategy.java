package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the PAUSE_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're currently in stanza mode
 * 2. Transitions from STANZA mode back to VOID mode
 * 3. Updates database status to "paused"
 * 4. Gets Erik's response acknowledging the pause
 * 
 * The stanza can be resumed later with the CONTINUE_STANZA flag.
 */
@Component
public class PauseStanzaStrategy implements FlowStrategy {

    private static final Logger log = LoggerFactory.getLogger(PauseStanzaStrategy.class);

    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;

    public PauseStanzaStrategy(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        // Validation: Check if already in void mode
        if (state.isInVoidMode()) {
            log.warn("Attempt to pause stanza while already in void mode");
            return "[System] Already in void mode.\n";
        }

        log.info("Pausing stanza");

        try {
            // Transition to void mode
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.PAUSED);

            // Update database status
            Long stanzaId = state.getActiveStanzaId();
            if (stanzaId != null) {
                try {
                    persistenceService.updateStatus(stanzaId, "paused");
                } catch (Exception e) {
                    log.warn("Failed to update stanza status in database", e);
                }
            }

            // Get Erik's response
            String response = conversationService.converseWithErik(state, userInput);
            
            log.info("Stanza paused successfully");
            return "\n[Erik] " + response + "\n";

        } catch (Exception e) {
            log.error("Failed to pause stanza", e);
            
            // Rollback state changes on error
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            
            return "\n[System] Failed to pause stanza.\n";
        }
    }
}