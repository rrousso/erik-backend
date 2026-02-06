package com.github.rrousso.erik_core.dto.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Extraction DTO for when the narrator describes or changes a character's
 * physical appearance.
 * 
 * This captures visual details that should update the character's
 * blueprint tier3_anchors field (physical description).
 * 
 * Examples:
 * - Narrator describes Joshua as "tall with messy dark hair" → new anchors
 * - Character changes clothes → updated anchors
 * - Character gets injured → updated anchors
 */
public class BlueprintUpdate {
    
    private String characterName;
    private List<String> updatedAnchors = new ArrayList<>();
    private String reason;  // e.g., "narrator described appearance", "character changed clothes"
    
    public BlueprintUpdate() {}
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public List<String> getUpdatedAnchors() {
        return updatedAnchors;
    }
    
    public void setUpdatedAnchors(List<String> updatedAnchors) {
        this.updatedAnchors = updatedAnchors != null ? updatedAnchors : new ArrayList<>();
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public String toString() {
        return String.format("BlueprintUpdate[character=%s, anchors=%d, reason=%s]",
            characterName, updatedAnchors.size(), reason);
    }
}