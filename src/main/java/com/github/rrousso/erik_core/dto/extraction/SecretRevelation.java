package com.github.rrousso.erik_core.dto.extraction;

/**
 * DTO representing a secret revelation - when a secret is exposed or suspected.
 * 
 * Maps to the JSON structure:
 * {
 *   "secretDescription": "which secret (match from doesNotKnow list)",
 *   "characterName": "who discovered it",
 *   "newState": "KNOWS" | "SUSPICIOUS",
 *   "howRevealed": "description of how it was revealed"
 * }
 * 
 * This will be used to update CharacterSecretState entries in the database,
 * moving secrets from UNAWARE → SUSPICIOUS or SUSPICIOUS → KNOWS.
 */
public class SecretRevelation {
    
    private String secretDescription;
    private String characterName;
    private String newState;  // KNOWS, SUSPICIOUS
    private String howRevealed;
    
    // === CONSTRUCTORS ===
    
    public SecretRevelation() {}
    
    public SecretRevelation(String secretDescription, String characterName, String newState, String howRevealed) {
        this.secretDescription = secretDescription;
        this.characterName = characterName;
        this.newState = newState;
        this.howRevealed = howRevealed;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getSecretDescription() {
        return secretDescription;
    }
    
    public void setSecretDescription(String secretDescription) {
        this.secretDescription = secretDescription;
    }
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public String getNewState() {
        return newState;
    }
    
    public void setNewState(String newState) {
        this.newState = newState;
    }
    
    public String getHowRevealed() {
        return howRevealed;
    }
    
    public void setHowRevealed(String howRevealed) {
        this.howRevealed = howRevealed;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if character now fully knows the secret
     */
    public boolean isFullyKnown() {
        return "KNOWS".equalsIgnoreCase(newState);
    }
    
    /**
     * Check if character is now suspicious
     */
    public boolean isSuspicious() {
        return "SUSPICIOUS".equalsIgnoreCase(newState);
    }
    
    @Override
    public String toString() {
        return String.format("SecretRevelation[%s now %s secret: %s]", 
            characterName, newState, secretDescription);
    }
}