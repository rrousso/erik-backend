package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.EmergentCharacterExtraction;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

/**
 * Applier for emergent characters detected during extraction.
 * 
 * When the analytical LLM detects a character in the narration that is NOT
 * in the existing character list, it produces a full character definition.
 * This applier creates a fully populated StanzaCharacter entity from that data.
 * 
 * This replaces the old "hollow shell" creation that was in CharacterAppearanceApplier.
 * 
 * IMPORTANT: This applier must run BEFORE CharacterAppearanceApplier so that
 * when the appearance applier processes the APPEARED entry for the same character,
 * it finds an existing entity and just updates presence_status instead of
 * creating a hollow shell.
 */
@Component
public class EmergentCharacterApplier implements ExtractionApplier<EmergentCharacterExtraction> {
    
    private static final Logger log = LoggerFactory.getLogger(EmergentCharacterApplier.class);
    
    private static final int MAX_NAME_LENGTH = 100;

    @Override
    public void apply(Stanza stanza, EmergentCharacterExtraction extraction) {
        // Validate name
        if (extraction.getCharacterName() == null || extraction.getCharacterName().isBlank()) {
            log.warn("[EmergentCharacterApplier] Skipping extraction with empty character name");
            return;
        }
        
        if (extraction.getCharacterName().length() > MAX_NAME_LENGTH) {
            log.warn("[EmergentCharacterApplier] Character name '{}' exceeds {} characters - skipping", 
                extraction.getCharacterName(), MAX_NAME_LENGTH);
            return;
        }
        
        // Check if character already exists (avoid duplicates)
        Optional<StanzaCharacter> existing = stanza.findCharacterByName(extraction.getCharacterName());
        if (existing.isPresent()) {
            log.info("[EmergentCharacterApplier] Character '{}' already exists - skipping emergent creation", 
                extraction.getCharacterName());
            return;
        }
        
        // Create fully populated character
        StanzaCharacter character = new StanzaCharacter(stanza, extraction.getCharacterName());
        
        // Canon role - prefix with EMERGENT so it's trackable, but include real role
        String role = extraction.getCanonRole();
        if (role != null && !role.isBlank()) {
            character.setCanonRole("EMERGENT - " + role);
        } else {
            character.setCanonRole("EMERGENT - original");
        }
        
        // Core fields
        character.setPresenceStatus("present"); // They just appeared
        character.setEmotionalState(
            extraction.getCurrentEmotionalState() != null ? 
            extraction.getCurrentEmotionalState() : "Unknown");
        character.setRelationshipToUser(
            extraction.getRelationshipToUser() != null ? 
            extraction.getRelationshipToUser() : "Unknown - newly encountered");
        
        // Blueprint tier 1 - Archetype & Speech Pattern
        if (extraction.getTier1Essentials() != null && !extraction.getTier1Essentials().isBlank()) {
            character.setBlueprintTier1Essentials(extraction.getTier1Essentials());
        }
        
        // Blueprint tier 2 - Goals & Fears
        if (extraction.getTier2Motivators() != null && !extraction.getTier2Motivators().isBlank()) {
            character.setBlueprintTier2Motivators(extraction.getTier2Motivators());
        }
        
        // Blueprint tier 3 - Visual anchors
        if (extraction.getTier3Anchors() != null && !extraction.getTier3Anchors().isEmpty()) {
            character.setBlueprintTier3Anchors(
                extraction.getTier3Anchors().toArray(new String[0]));
        }
        
        // Add to stanza
        stanza.getCharacters().add(character);
        
        log.info("[EmergentCharacterApplier] Created EMERGENT character '{}' with full blueprint " +
                "(role: {}, emotional: {}, relationship: {}, anchors: {})", 
            character.getName(),
            character.getCanonRole(),
            character.getEmotionalState(),
            character.getRelationshipToUser(),
            character.getBlueprintTier3Anchors() != null ? character.getBlueprintTier3Anchors().length : 0);
    }
    
    @Override
    public String getTypeName() {
        return "EmergentCharacter";
    }
}