package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.BlueprintUpdate;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

/**
 * Applier for blueprint/visual appearance updates.
 * 
 * When the narrator describes or changes a character's physical appearance,
 * this applier updates their tier3_anchors field.
 * 
 * This replaces the old anchors entirely — the extraction prompt is instructed
 * to provide ALL current visual details, not just deltas.
 */
@Component
public class BlueprintUpdateApplier implements ExtractionApplier<BlueprintUpdate> {
    
    private static final Logger log = LoggerFactory.getLogger(BlueprintUpdateApplier.class);
    
    @Override
    public void apply(Stanza stanza, BlueprintUpdate update) {
        if (update.getCharacterName() == null || update.getCharacterName().isBlank()) {
            log.warn("[BlueprintUpdateApplier] Skipping update with empty character name");
            return;
        }
        
        if (update.getUpdatedAnchors() == null || update.getUpdatedAnchors().isEmpty()) {
            log.warn("[BlueprintUpdateApplier] Skipping update for '{}' with no anchors", 
                update.getCharacterName());
            return;
        }
        
        // Find character by name (case-insensitive)
        Optional<StanzaCharacter> charOpt = stanza.findCharacterByName(update.getCharacterName());
        
        if (charOpt.isEmpty()) {
            log.warn("[BlueprintUpdateApplier] Character '{}' not charOpt - skipping blueprint update",
                update.getCharacterName());
            return;
        }
        
        StanzaCharacter character = charOpt.get();
        String[] newAnchors = update.getUpdatedAnchors().toArray(new String[0]);
        
        String[] oldAnchors = character.getBlueprintTier3Anchors();
        character.setBlueprintTier3Anchors(newAnchors);
        
        log.info("[BlueprintUpdateApplier] Updated tier3_anchors for '{}': {} → {} (reason: {})",
            character.getName(),
            oldAnchors != null ? String.join(", ", oldAnchors) : "[empty]",
            String.join(", ", newAnchors),
            update.getReason());
    }
}