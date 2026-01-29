package com.github.rrousso.erik_core.dto.extraction;

/**
 * DTO representing a knowledge transfer - when a character learns something.
 * 
 * Maps to the JSON structure:
 * {
 *   "characterName": "name of character who learned something",
 *   "whatTheyLearned": "description of the knowledge gained",
 *   "howLearned": "OBSERVED" | "TOLD" | "INFERRED"
 * }
 * 
 * This will be used to create CharacterKnowledge entries in the database,
 * tracking what each character knows and how they learned it.
 */
public class KnowledgeTransfer {
    
    private String characterName;
    private String whatTheyLearned;
    private String howLearned;  // OBSERVED, TOLD, INFERRED
    
    // === CONSTRUCTORS ===
    
    public KnowledgeTransfer() {}
    
    public KnowledgeTransfer(String characterName, String whatTheyLearned, String howLearned) {
        this.characterName = characterName;
        this.whatTheyLearned = whatTheyLearned;
        this.howLearned = howLearned;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getCharacterName() {
        return characterName;
    }
    
    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }
    
    public String getWhatTheyLearned() {
        return whatTheyLearned;
    }
    
    public void setWhatTheyLearned(String whatTheyLearned) {
        this.whatTheyLearned = whatTheyLearned;
    }
    
    public String getHowLearned() {
        return howLearned;
    }
    
    public void setHowLearned(String howLearned) {
        this.howLearned = howLearned;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if knowledge was observed
     */
    public boolean wasObserved() {
        return "OBSERVED".equalsIgnoreCase(howLearned);
    }
    
    /**
     * Check if knowledge was told
     */
    public boolean wasTold() {
        return "TOLD".equalsIgnoreCase(howLearned);
    }
    
    /**
     * Check if knowledge was inferred
     */
    public boolean wasInferred() {
        return "INFERRED".equalsIgnoreCase(howLearned);
    }
    
    @Override
    public String toString() {
        return String.format("KnowledgeTransfer[%s learned '%s' via %s]", 
            characterName, whatTheyLearned, howLearned);
    }
}