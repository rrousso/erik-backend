package com.github.rrousso.erik_core.services.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.services.session.SynopsisGeneratorService;

/**
 * Service for creating CompletedStanza objects when a stanza ends or is abandoned.
 * 
 * Centralizes the logic for:
 * - Generating a quick synopsis of the stanza
 * - Creating the CompletedStanza value object
 * - Clearing the stanza history
 * 
 * Used by both EndStanzaStrategy and AbandonStanzaStrategy to avoid code duplication.
 */
@Service
public class StanzaCompletionService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaCompletionService.class);
    
    private final SynopsisGeneratorService synopsisGenerator;
    
    public StanzaCompletionService(SynopsisGeneratorService synopsisGenerator) {
        this.synopsisGenerator = synopsisGenerator;
    }
    
    /**
     * Create a CompletedStanza from the current session state.
     * 
     * This method:
     * 1. Generates a quick synopsis from the stanza history
     * 2. Creates a CompletedStanza with the synopsis and initialized stanza
     * 3. Clears the stanza history (ready for next stanza)
     * 
     * @param state The current session state
     * @return A CompletedStanza object with synopsis and metadata
     */
    public CompletedStanza createCompletedStanza(SessionState state) {
        String quickSynopsis = "";
        
        try {
            quickSynopsis = synopsisGenerator.generateQuickSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            log.error("Failed to generate quick synopsis", e);
            // Continue with empty synopsis rather than failing
        }
        
        CompletedStanza completed = new CompletedStanza(
            quickSynopsis, 
            state.getInitializedStanza()
        );
        
        // Clear history to prepare for next stanza
        state.getStanzaHistory().clearHistory();
        
        return completed;
    }
}