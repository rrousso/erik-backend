package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.TensionChange;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.Tension;

/**
 * Applier for tension change extractions.
 * 
 * Updates Tension entities when story threads escalate, de-escalate, resolve, or emerge.
 * 
 * Process:
 * 1. Validate description and reason lengths
 * 2. If tension is newly created:
 *    - Create new Tension entity (emergent tension)
 *    - Set initial pressure and metadata
 *    - Add to stanza's tensions collection
 * 3. If tension already exists:
 *    - Find the Tension by matching description
 *    - If resolved: Mark as RESOLVED
 *    - If pressure changed: Update pressure level and beat
 * 
 * Tension changes represent story thread developments:
 * - "Will Scott control his werewolf side?" escalates → pressure 3 → 5
 * - "Can they escape the Alpha?" de-escalates → pressure 7 → 4
 * - "Who is the mysterious benefactor?" resolves → RESOLVED
 * - NEW: "Will Derek betray the pack?" created → pressure 6 (emergent)
 * 
 * Tensions are the narrative threads that drive the story forward and create
 * dramatic questions the audience wants answered.
 */
@Component
public class TensionChangeApplier implements ExtractionApplier<TensionChange> {
    
    private static final Logger log = LoggerFactory.getLogger(TensionChangeApplier.class);
    
    private static final int MAX_DESCRIPTION_LENGTH = 400;
    private static final int MAX_REASON_LENGTH = 200;

    @Override
    public void apply(Stanza stanza, TensionChange change) {
        // Validate lengths
        if (change.getTensionDescription() != null && change.getTensionDescription().length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[TensionChangeApplier] Tension description exceeds recommended {} characters: '{}'", 
                MAX_DESCRIPTION_LENGTH,
                change.getTensionDescription().substring(0, Math.min(100, change.getTensionDescription().length())) + "...");
        }
        if (change.getReason() != null && change.getReason().length() > MAX_REASON_LENGTH) {
            log.warn("[TensionChangeApplier] Tension reason exceeds recommended {} characters", 
                MAX_REASON_LENGTH);
        }
        
        // Handle newly created tensions (emergent)
        if (change.isCreated()) {
            Tension newTension = new Tension();
            
            newTension.setDescription(change.getTensionDescription());
            newTension.setPressure(change.getNewPressure() != null ? change.getNewPressure() : 5); // Default to mid-level
            newTension.setStanza(stanza);
            newTension.setStatus("ACTIVE");
            newTension.setSource("NARRATOR_EMERGENT");  
            newTension.setCreatedBeat(stanza.getCurrentBeatNumber()); 
            newTension.setUpdatedBeat(stanza.getCurrentBeatNumber());
            
            stanza.getTensions().add(newTension);
            
            log.info("[TensionChangeApplier] Created EMERGENT tension: '{}' at pressure {}", 
                change.getTensionDescription(), 
                newTension.getPressure());
            return;
        }
        
        // Handle existing tension updates
        // Find the Tension by matching description
        Optional<Tension> tensionOpt = 
            stanza.getTensions().stream()
                .filter(t -> {
                    String tensionDesc = t.getDescription();
                    String changeTensionDesc = change.getTensionDescription();
                    
                    // Try exact match first (case-insensitive)
                    if (tensionDesc.equalsIgnoreCase(changeTensionDesc)) {
                        return true;
                    }
                    
                    // Try partial match (contains)
                    if (tensionDesc.toLowerCase().contains(changeTensionDesc.toLowerCase()) ||
                        changeTensionDesc.toLowerCase().contains(tensionDesc.toLowerCase())) {
                        return true;
                    }
                    
                    return false;
                })
                .findFirst();
        
        if (!tensionOpt.isPresent()) {
            log.warn("[TensionChangeApplier] Tension matching '{}' not found in stanza - skipping", 
                change.getTensionDescription());
            return;
        }
        
        Tension tension = tensionOpt.get();
        
        // Handle resolution
        if (change.isResolved()) {
            tension.resolve();
            
            log.info("[TensionChangeApplier] Tension RESOLVED: '{}'", 
                tension.getDescription());
            return;
        }
        
        // Handle pressure change
        Integer oldPressure = tension.getPressure();
        Integer newPressure = change.getNewPressure();
        
        if (newPressure != null && !newPressure.equals(oldPressure)) {
            tension.setPressure(newPressure);        
            tension.setUpdatedBeat(stanza.getCurrentBeatNumber());
            
            if (newPressure > oldPressure) {
                log.info("[TensionChangeApplier] Tension ESCALATED: '{}' from {} to {}", 
                    tension.getDescription(), oldPressure, newPressure);
            } else if (newPressure < oldPressure) {
                log.info("[TensionChangeApplier] Tension DE-ESCALATED: '{}' from {} to {}", 
                    tension.getDescription(), oldPressure, newPressure);
            }
        }
        
        log.debug("[TensionChangeApplier] Updated tension: {}", change);
    }
    
    @Override
    public String getTypeName() {
        return "TensionChange";
    }
}