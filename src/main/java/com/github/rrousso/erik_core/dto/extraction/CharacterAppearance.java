package com.github.rrousso.erik_core.dto.extraction;

/**
 * DTO representing a character appearance change - when characters enter/leave.
 * 
 * Maps to the JSON structure:
 * {
 *   "characterName": "name",
 *   "changeType": "APPEARED" | "LEFT" | "MENTIONED",
 *   "context": "brief context of the change"
 * }
 * 
 * This will be used to update StanzaCharacter presence_status in the database:
 * - APPEARED: potential/background → present
 * - LEFT: present → potential/background
 * - MENTIONED: remains background but was referenced
 */
public class CharacterAppearance {
    
    private String characterName;
    private String changeType;  // APPEARED, LEFT, MENTIONED
    private String context;
    
    // === CONSTRUCTORS ===
    
    public CharacterAppearance() {}
    
    public CharacterAppearance(String characterName, String changeType, String context) {
        this.characterName = characterName;
        this.changeType = changeType;
        this.context = context;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public String getContext() {
        return context;
    }
    
    public void setContext(String context) {
        this.context = context;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if character appeared in scene
     */
    public boolean isAppearance() {
        return "APPEARED".equalsIgnoreCase(changeType);
    }
    
    /**
     * Check if character left scene
     */
    public boolean isDeparture() {
        return "LEFT".equalsIgnoreCase(changeType);
    }
    
    /**
     * Check if character was only mentioned
     */
    public boolean isMention() {
        return "MENTIONED".equalsIgnoreCase(changeType);
    }
    
    @Override
    public String toString() {
        return String.format("CharacterAppearance[%s: %s - %s]", 
            characterName, changeType, context);
    }
}