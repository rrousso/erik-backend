package com.github.rrousso.erik_core.dto.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a fact discovery during narrative.
 * 
 * Combines fact establishment with immediate knowledge transfer.
 * This mirrors how facts actually emerge in storytelling: when something is revealed,
 * the characters present learn about it simultaneously.
 * 
 * Two modes:
 * 1. NEW FACT: Includes tempId, statement, truthValue, allowedRevealModes
 * 2. EXISTING FACT: References existingFactHash from registry
 * 
 * Both modes include discoveredBy list - who learned this fact and how.
 * 
 * Examples:
 * 
 * New fact discovered by multiple characters:
 * {
 *   "tempId": "discovery_1",
 *   "statement": "The box contains gold",
 *   "truthValue": true,
 *   "allowedRevealModes": "OBSERVED",
 *   "discoveredBy": [
 *     {"characterName": "Derek", "howLearned": "OBSERVED"},
 *     {"characterName": "User", "howLearned": "OBSERVED"}
 *   ]
 * }
 * 
 * Existing fact learned by new character:
 * {
 *   "existingFactHash": "a3f8b2c1",
 *   "discoveredBy": [
 *     {"characterName": "Stiles", "howLearned": "TOLD"}
 *   ]
 * }
 */
public class FactDiscovery {
    
    // === NEW FACT FIELDS ===
    private String tempId;              // Temporary ID for this extraction
    private String statement;           // The fact content
    private Boolean truthValue;         // True or false
    private String allowedRevealModes;  // How this fact can be learned (null = public)
    
    // === EXISTING FACT REFERENCE ===
    private String existingFactHash;    // Reference to fact in registry (8-char hash)
    
    // === DISCOVERY INFO (BOTH MODES) ===
    private List<CharacterDiscovery> discoveredBy = new ArrayList<>();
    
    // === INNER CLASS ===
    
    /**
     * Represents a single character learning this fact.
     */
    public static class CharacterDiscovery {
        private String characterName;
        private String howLearned;  // OBSERVED, TOLD, INFERRED, SENSED_SPECIAL, DOCUMENTED
        
        public CharacterDiscovery() {}
        
        public CharacterDiscovery(String characterName, String howLearned) {
            this.characterName = characterName;
            this.howLearned = howLearned;
        }
        
        public String getCharacterName() {
            return characterName;
        }
        
        public void setCharacterName(String characterName) {
            this.characterName = characterName;
        }
        
        public String getHowLearned() {
            return howLearned;
        }
        
        public void setHowLearned(String howLearned) {
            this.howLearned = howLearned;
        }
    }
    
    // === CONSTRUCTORS ===
    
    public FactDiscovery() {}
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if this is a new fact (has statement)
     */
    public boolean isNewFact() {
        return statement != null && !statement.isEmpty();
    }
    
    /**
     * Check if this references an existing fact (has hash)
     */
    public boolean isExistingFact() {
        return existingFactHash != null && !existingFactHash.isEmpty();
    }
    
    /**
     * Check if this fact has reveal restrictions
     */
    public boolean isRestricted() {
        return allowedRevealModes != null && !allowedRevealModes.isEmpty();
    }
    
    /**
     * Validate that discovery has required fields
     */
    public boolean isValid() {
        // Must be either new fact or existing reference
        if (!isNewFact() && !isExistingFact()) {
            return false;
        }
        
        // Must have at least one discoverer
        if (discoveredBy == null || discoveredBy.isEmpty()) {
            return false;
        }
        
        // If new fact, must have statement
        if (isNewFact() && (statement == null || statement.isEmpty())) {
            return false;
        }
        
        return true;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getTempId() {
        return tempId;
    }
    
    public void setTempId(String tempId) {
        this.tempId = tempId;
    }
    
    public String getStatement() {
        return statement;
    }
    
    public void setStatement(String statement) {
        this.statement = statement;
    }
    
    public Boolean getTruthValue() {
        return truthValue;
    }
    
    public void setTruthValue(Boolean truthValue) {
        this.truthValue = truthValue;
    }
    
    public String getAllowedRevealModes() {
        return allowedRevealModes;
    }
    
    public void setAllowedRevealModes(String allowedRevealModes) {
        this.allowedRevealModes = allowedRevealModes;
    }
    
    public String getExistingFactHash() {
        return existingFactHash;
    }
    
    public void setExistingFactHash(String existingFactHash) {
        this.existingFactHash = existingFactHash;
    }
    
    public List<CharacterDiscovery> getDiscoveredBy() {
        return discoveredBy;
    }
    
    public void setDiscoveredBy(List<CharacterDiscovery> discoveredBy) {
        this.discoveredBy = discoveredBy != null ? discoveredBy : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        if (isNewFact()) {
            return String.format("FactDiscovery[NEW: %s, discoverers: %d]", 
                statement, 
                discoveredBy.size());
        } else {
            return String.format("FactDiscovery[EXISTING: %s, discoverers: %d]", 
                existingFactHash, 
                discoveredBy.size());
        }
    }
}