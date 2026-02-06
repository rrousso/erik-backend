package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.stanza.EventCompressionService;
import com.github.rrousso.erik_core.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling narration in STANZA mode (talking to the Narrator).
 * 
 * This mode is active during an ongoing stanza.
 * The Narrator responds with narrative text, and we extract state changes
 * based on the configured extraction frequency.
 */
@Component
public class StanzaModeStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaModeStrategy.class);
    
    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;
    private final StanzaExtractionService extractionService;
    private final EventCompressionService compressionService;
    
    public StanzaModeStrategy(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            StanzaExtractionService extractionService,
            EventCompressionService compressionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.extractionService = extractionService;
        this.compressionService = compressionService;
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
     * This includes incrementing the exchange counter and conditionally extracting state changes.
     * 
     * Extraction frequency is controlled by ExtractionConfig:
     * - erik.extraction.frequency: How often to extract (1 = every exchange, 2 = every other, etc.)
     * - erik.extraction.enabled: Whether extraction is enabled at all
     * - erik.extraction.always-extract-on-start: Always extract first exchange
     * - erik.extraction.always-extract-on-end: Always extract last exchange
     * 
     * Event compression is controlled by EventCompressionService:
     * - erik.events.compress-frequency: How often to compress (default: 20)
     * - erik.events.keep-recent-exchanges: How many recent exchanges to keep (default: 10)
     * - erik.events.always-keep-major: Whether to keep major events (default: true)
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
            int exchangeNumber = stanza.getCurrentExchange();
            
            // Process extraction - service decides whether to extract based on config
            ConversationHistory history = state.getStanzaHistory();
            boolean isFirstExchange = (exchangeNumber == 1);
            boolean isFinalExchange = false; // We don't know if it's final in regular mode

            boolean extracted = extractionService.processExtraction(
                stanza, history, exchangeNumber, isFirstExchange, isFinalExchange);

            if (extracted) {
                log.debug("[StanzaModeStrategy] Extraction completed for exchange {}", exchangeNumber);
            }
            
            // Check if we should compress events
            if (compressionService.shouldCompress(exchangeNumber)) {
                log.debug("[StanzaModeStrategy] Compressing events (exchange {})", exchangeNumber);
                int compressed = compressionService.compressEvents(stanza);
                if (compressed > 0) {
                    log.info("[StanzaModeStrategy] Compressed {} events", compressed);
                }
            }
            
            // Save all changes in one transaction (exchange count + any extractions + any compressions)
            persistenceService.save(stanza);
            
        } catch (Exception e) {
            log.warn("Failed to update stanza state in database", e);
            // Don't fail the whole exchange if state update fails
        }
    }
}