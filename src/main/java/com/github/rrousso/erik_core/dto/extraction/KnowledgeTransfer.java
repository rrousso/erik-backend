package com.github.rrousso.erik_core.dto.extraction;

public class KnowledgeTransfer {
    
    private String characterName;
    private String whatTheyLearned;        // Used for NEW facts
    private String existingFactHash;       // Used to reference existing facts
    private String howLearned;
    
    public KnowledgeTransfer() {}
    
    // Getters and setters
    
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
    
    public String getExistingFactHash() {
        return existingFactHash;
    }
    
    public void setExistingFactHash(String existingFactHash) {
        this.existingFactHash = existingFactHash;
    }
    
    public String getHowLearned() {
        return howLearned;
    }
    
    public void setHowLearned(String howLearned) {
        this.howLearned = howLearned;
    }
    
    /**
     * Check if this is a reference to an existing fact
     */
    public boolean isExistingFactReference() {
        return existingFactHash != null && !existingFactHash.isEmpty();
    }
    
    /**
     * Check if this is a new fact
     */
    public boolean isNewFact() {
        return whatTheyLearned != null && !whatTheyLearned.isEmpty();
    }
}