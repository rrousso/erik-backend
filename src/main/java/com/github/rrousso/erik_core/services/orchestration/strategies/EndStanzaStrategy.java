package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.orchestration.StanzaCompletionService;
import com.github.rrousso.erik_core.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the END_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're currently in stanza mode
 * 2. Gets Narrator's closing narration
 * 3. Transitions from STANZA mode back to VOID mode
 * 4. Updates database status to "completed"
 * 5. Extracts final state changes
 * 6. Creates completed stanza record with quick synopsis
 * 7. Gets Erik's reflection response
 */
@Component
public class EndStanzaStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(EndStanzaStrategy.class);
    
    private final ConversationService conversationService;
    private final StanzaCompletionService completionService;
    private final StanzaPersistenceService persistenceService;
    private final StanzaExtractionService extractionService;
    
    public EndStanzaStrategy(
            ConversationService conversationService,
            StanzaCompletionService completionService,
            StanzaExtractionService extractionService,
            StanzaPersistenceService persistenceService) {
        this.conversationService = conversationService;
        this.completionService = completionService;
        this.persistenceService = persistenceService;
        this.extractionService = extractionService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        // Validation: Check if in void mode
        if (state.isInVoidMode()) {
            log.warn("Attempt to end stanza while in void mode");
            return "[System] No active stanza to end.\n";
        }
        
        log.info("Ending stanza");
        StringBuilder builder = new StringBuilder();

        try {
            // Get closing narration with user's actual input
            String closure = conversationService.converseWithNarrator(state, 
                userInput + " ((Bring the scene to a natural closing moment. NARRATION ONLY - no meta-commentary, no epilogue notes, no analysis. Just the final narrative moment.))");
            builder.append("\n[Narration - Closing] ");
            builder.append(closure);
        
            // Transition to void mode
            state.enterVoidMode();
            
            // Create completed stanza record
            CompletedStanza completed = completionService.createCompletedStanza(state);
            state.setCompletedStanza(completed);
            state.setStanzaStatus(StanzaStatus.COMPLETED);
            
            // Update database with final state
            Long stanzaId = state.getActiveStanzaId();
            if (stanzaId != null) {
                try {
                    Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
                    
                    // Increment exchange count for final narration
                    stanza.incrementExchange();
                    
                 // Process final extraction (service decides based on config)
                    ConversationHistory history = state.getStanzaHistory();
                    int exchangeNumber = stanza.getCurrentExchange();
                    boolean isFinalExchange = true;
                    boolean isFirstExchange = false;

                    boolean extracted = extractionService.processExtraction(
                        stanza, history, exchangeNumber, isFirstExchange, isFinalExchange);

                    if (extracted) {
                        log.debug("[EndStanzaStrategy] Final extraction completed for exchange {}", exchangeNumber);
                    } else {
                        log.debug("[EndStanzaStrategy] Final extraction skipped per configuration");
                    }
                    
                    // Mark as completed
                    stanza.setStatus("completed");
                    
                    // Store quick synopsis
                    stanza.setQuickSynopsis(completed.getQuickSynopsis());
                    
                    // Single database write
                    persistenceService.save(stanza);
                    
                } catch (Exception e) {
                    log.warn("Failed to update completed stanza in database", e);
                }
            }
            
            // Display completion messages
            builder.append("\n\n[STANZA END]\n");
            builder.append("\n[System] Here's the quick synopsis:\n");
            builder.append(completed.getQuickSynopsis());
            builder.append("\n");
            
            // Get Erik's reflection
            try {
                String erikReflection = conversationService.converseWithErik(state, 
                    "Stanza finished! What did you think about it?");
                builder.append("\n[Erik] ");
                builder.append(erikReflection);
                builder.append("\n");
            } catch (Exception e) {
                log.warn("Failed to get Erik's reflection on stanza end", e);
            }
            
            log.info("Stanza ended successfully");
            return builder.toString();
            
        } catch (Exception e) {
            log.error("Failed to end stanza", e);
            
            // Rollback state changes on error
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            
            return "[System] Failed to end stanza.\n";
        }
    }
}