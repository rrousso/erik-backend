package com.github.rrousso.erik_core.dto.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a fully-defined emergent character detected during extraction.
 * 
 * When the analytical LLM detects a character in the narration that is NOT
 * in the provided character list, it must emit a full definition here.
 * 
 * For IP worlds: Use canonical data from the IP (lore-accurate).
 * For original worlds: Invent details based on what the narration shows,
 * filling in anything missing with creative but consistent details.
 * 
 * This mirrors the initialization architect's character format but is
 * produced mid-stanza by the extraction LLM.
 */
public class EmergentCharacterExtraction {
    
    private String characterName;
    private String canonRole;           // Role in the IP, or "original" for invented characters
    private String currentEmotionalState;
    private String relationshipToUser;
    
    // Blueprint tiers (same structure as initialization)
    private String tier1Essentials;     // Archetype & Speech Pattern
    private String tier2Motivators;     // Primary Goal & Major Fear
    private List<String> tier3Anchors = new ArrayList<>();  // Physical appearance details
    
    // === CONSTRUCTORS ===
    
    public EmergentCharacterExtraction() {}
    
    // === GETTERS AND SETTERS ===
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public String getCanonRole() {
        return canonRole;
    }
    
    public void setCanonRole(String canonRole) {
        this.canonRole = canonRole;
    }
    
    public String getCurrentEmotionalState() {
        return currentEmotionalState;
    }
    
    public void setCurrentEmotionalState(String currentEmotionalState) {
        this.currentEmotionalState = currentEmotionalState;
    }
    
    public String getRelationshipToUser() {
        return relationshipToUser;
    }
    
    public void setRelationshipToUser(String relationshipToUser) {
        this.relationshipToUser = relationshipToUser;
    }
    
    public String getTier1Essentials() {
        return tier1Essentials;
    }
    
    public void setTier1Essentials(String tier1Essentials) {
        this.tier1Essentials = tier1Essentials;
    }
    
    public String getTier2Motivators() {
        return tier2Motivators;
    }
    
    public void setTier2Motivators(String tier2Motivators) {
        this.tier2Motivators = tier2Motivators;
    }
    
    public List<String> getTier3Anchors() {
        return tier3Anchors;
    }
    
    public void setTier3Anchors(List<String> tier3Anchors) {
        this.tier3Anchors = tier3Anchors != null ? tier3Anchors : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return String.format("EmergentCharacterExtraction[name=%s, role=%s, anchors=%d]",
            characterName, canonRole, tier3Anchors.size());
    }
}