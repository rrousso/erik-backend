package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling narration in STANZA mode (talking to the Narrator).
 * 
 * This mode is active during an ongoing stanza.
 * The Narrator responds with narrative text, and we extract state changes.
 */
@Component
public class StanzaModeStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaModeStrategy.class);
    
    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;
    private final StanzaExtractionService extractionService;
    
    public StanzaModeStrategy(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            StanzaExtractionService extractionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.extractionService = extractionService;
    }
    
    @Override
    public String execute(String userInput, SessionState state) {
        try {
            String narration = conversationService.converseWithNarrator(state, userInput);
            
            // Update database state if we have a stanza ID
            updateStanzaState(state, userInput, narration);
            
            return "\n[Narration] " + narration;
            
        } catch (Exception e) {
            log.error("Error in stanza mode", e);
            return "\n[System] An error occurred. Please try again.\n";
        }
    }
    
    /**
     * Update the stanza's state in the database after each exchange.
     * This includes incrementing the exchange counter and extracting state changes.
     */
    private void updateStanzaState(SessionState state, String userInput, String narration) {
        Long stanzaId = state.getActiveStanzaId();
        
        if (stanzaId == null) {
            return; // No database record to update
        }
        
        try {
            // Load stanza once, do all updates in memory, save once
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
            
            // Increment exchange counter
            stanza.incrementExchange();
            
            // Extract and apply state changes
            extractionService.extractAndUpdate(stanza, userInput, narration);
            
            // Save all changes in one transaction
            persistenceService.save(stanza);
            
        } catch (Exception e) {
            log.warn("Failed to update stanza state in database", e);
            // Don't fail the whole exchange if state update fails
        }
    }
}