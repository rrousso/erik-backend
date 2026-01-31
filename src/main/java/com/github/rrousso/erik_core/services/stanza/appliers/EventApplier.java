package com.github.rrousso.erik_core.services.stanza.appliers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.persistence.entities.Beat;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;

/**
 * Applier for event extractions.
 * 
 * Creates StanzaEvent entries in the database for each extracted event.
 * 
 * BEAT INTEGRATION:
 * - Links events to current beat (via beat relationship)
 * - Sets both beat (entity) and beatNumber (denormalized field)
 * - When beat ends, minor events will be deleted (major events kept)
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
        
        // Set exchange number
        event.setExchangeNumber(stanza.getCurrentExchange());
        
        // NEW: Link to current beat (entity + denormalized beatNumber)
        Beat currentBeat = stanza.getCurrentBeat();
        if (currentBeat != null) {
            event.setBeat(currentBeat);  // This also sets beatNumber
        } else {
            // Fallback: just set beatNumber if no beat entity found
            log.warn("[EventApplier] No current beat found, using beat number from stanza");
            event.setBeatNumber(stanza.getCurrentBeatNumber());  // FIXED: use getCurrentBeatNumber()
        }
        
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
        
        log.debug("[EventApplier] Created event: {} (beat {}, exchange {}, {})", 
            description, 
            event.getBeatNumber(),
            stanza.getCurrentExchange(),
            extraction.getSignificance());
    }
    
    @Override
    public String getTypeName() {
        return "Event";
    }
}