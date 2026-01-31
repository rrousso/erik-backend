package com.github.rrousso.erik_core.services.stanza.appliers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;

/**
 * Applier for event extractions.
 * 
 * Creates StanzaEvent entries in the database for each extracted event.
 * 
 * Process:
 * 1. Validate description length (max 280 chars)
 * 2. Create a new StanzaEvent entity
 * 3. Set description, beat/exchange numbers, involved characters, major flag
 * 4. Add to stanza's events list (cascade will persist it)
 * 
 * Events represent things that happened during the exchange:
 * - "Derek revealed his werewolf identity to Stiles"
 * - "The pack decided to investigate the Alpha"
 * - "Scott transformed under the full moon"
 */
@Component
public class EventApplier implements ExtractionApplier<EventExtraction> {
    
    private static final Logger log = LoggerFactory.getLogger(EventApplier.class);
    
    private static final int MAX_DESCRIPTION_LENGTH = 280;
    
    @Override
    public void apply(Stanza stanza, EventExtraction extraction) {
        // Validate description length
        String description = extraction.getDescription();
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[EventApplier] Event description exceeds {} characters and will be truncated: '{}'", 
                MAX_DESCRIPTION_LENGTH,
                description.substring(0, Math.min(100, description.length())) + "...");
            log.warn("[EventApplier] Consider adjusting extraction prompt to generate shorter descriptions");
        }
        
        // Create new event entity
        StanzaEvent event = new StanzaEvent();
        
        // Set the stanza relationship
        event.setStanza(stanza);
        
        // Set description (entity constructor handles truncation)
        event.setDescription(description);
        
        // Set when this happened (beat and exchange)
        event.setBeatNumber(stanza.getCurrentBeat());
        event.setExchangeNumber(stanza.getCurrentExchange());
        
        // Convert List<String> to comma-separated string for involved characters
        if (extraction.getCharactersInvolved() != null && !extraction.getCharactersInvolved().isEmpty()) {
            String involvedCharacters = String.join(",", extraction.getCharactersInvolved());
            event.setInvolvedCharacters(involvedCharacters);
        }
        
        // Set major flag based on significance
        event.setMajor(extraction.isMajor());
        
        // Add to stanza's events list
        // Because of cascade = CascadeType.ALL, this will be saved when the transaction commits
        stanza.getEvents().add(event);
        
        log.debug("[EventApplier] Created event: {} (exchange {}, {})", 
            description, 
            stanza.getCurrentExchange(),
            extraction.getSignificance());
    }
    
    @Override
    public String getTypeName() {
        return "Event";
    }
}