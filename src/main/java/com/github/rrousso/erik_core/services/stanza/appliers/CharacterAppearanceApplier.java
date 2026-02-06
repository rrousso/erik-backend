package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.CharacterAppearance;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

/**
 * Applier for character appearance/departure extractions.
 * 
 * Updates StanzaCharacter presence_status when characters enter, leave, or are mentioned.
 * Handles emergent characters (characters the narrator invented that weren't in the setup).
 * 
 * Process:
 * 1. Validate name and context lengths
 * 2. Find the character (or create if emergent)
 * 3. Update presence_status based on change type:
 *    - APPEARED: potential/background → present
 *    - LEFT: present → potential (could return)
 *    - MENTIONED: background → potential (if only background)
 * 
 * Appearance changes represent characters entering/leaving scenes:
 * - "Derek appeared at the lacrosse field" → present
 * - "Allison left the school" → potential
 * - "They mentioned Derek's uncle Peter" → background → potential
 * 
 * EMERGENT CHARACTERS:
 * If the narrator introduces someone not in the setup (e.g., "A mysterious stranger
 * approached"), we create them with minimal info flagged as "EMERGENT". 
 * Phase 3 (architect) can enhance them later with proper backstory/secrets.
 */
@Component
public class CharacterAppearanceApplier implements ExtractionApplier<CharacterAppearance> {
    
    private static final Logger log = LoggerFactory.getLogger(CharacterAppearanceApplier.class);
    
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CONTEXT_LENGTH = 200;

    @Override
    public void apply(Stanza stanza, CharacterAppearance appearance) {
        // Validate lengths
        if (appearance.getCharacterName() != null && appearance.getCharacterName().length() > MAX_NAME_LENGTH) {
            log.warn("[CharacterAppearanceApplier] Character name exceeds {} characters (will be rejected by database)", 
                MAX_NAME_LENGTH);
            return;
        }
        if (appearance.getContext() != null && appearance.getContext().length() > MAX_CONTEXT_LENGTH) {
            log.warn("[CharacterAppearanceApplier] Appearance context exceeds recommended {} characters", 
                MAX_CONTEXT_LENGTH);
        }
        
        // Find character (or create if emergent)        
        Optional<StanzaCharacter> charOpt = stanza.findCharacterByName(appearance.getCharacterName());
        
        StanzaCharacter character;
        
        if (!charOpt.isPresent()) {
            // EMERGENT CHARACTER - narrator invented them
            log.warn("[CharacterAppearanceApplier] Character '{}' not in setup - creating as EMERGENT", 
                appearance.getCharacterName());
            
            character = new StanzaCharacter(
                stanza, 
                appearance.getCharacterName()
            );
            
            // Flag as emergent with context
            character.setCanonRole("EMERGENT - " + 
                (appearance.getContext() != null ? appearance.getContext() : "appeared in narration"));
            
            // Minimal setup
            character.setPresenceStatus("present"); // They just appeared
            character.setEmotionalState("Unknown - needs architect setup");
            
            // Add to stanza
            stanza.getCharacters().add(character);
            
            log.info("[CharacterAppearanceApplier] Created EMERGENT character: {} (needs Phase 3 setup)", 
                appearance.getCharacterName());
            
        } else {
            character = charOpt.get();
        }
        
        // Handle the appearance change type
        if (appearance.isAppearance()) {
            // Character appeared in scene
            String oldStatus = character.getPresenceStatus();
            character.setPresenceStatus("present");
            
            log.debug("[CharacterAppearanceApplier] Character '{}' appeared (was: {}, now: present)", 
                character.getName(), oldStatus);
            
        } else if (appearance.isDeparture()) {
            // Character left scene
            String oldStatus = character.getPresenceStatus();
            character.setPresenceStatus("potential"); // Could return later
            
            log.debug("[CharacterAppearanceApplier] Character '{}' departed (was: {}, now: potential)", 
                character.getName(), oldStatus);
            
        } else if (appearance.isMention()) {
            // Character was mentioned but not present
            // Only promote from background to potential if mentioned
            if ("background".equals(character.getPresenceStatus())) {
                character.setPresenceStatus("potential");
                log.debug("[CharacterAppearanceApplier] Character '{}' mentioned (promoted background → potential)", 
                    character.getName());
            }
        }
    }
    
    @Override
    public String getTypeName() {
        return "CharacterAppearance";
    }
}