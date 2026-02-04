package com.github.rrousso.erik_core.dto.extraction;

/**
 * DTO representing a new fact established during the narrative.
 * 
 * Maps to the JSON structure:
 * {
 *   "statement": "The gift box contains a squeaky mouse",
 *   "truthValue": true,
 *   "allowedRevealModes": "OBSERVED"
 * }
 * 
 * Facts are truths about the world that exist but may not be known
 * by any character yet. They can be discovered later through
 * KnowledgeTransfer events.
 */
public class FactEstablishment {
    
    private String statement;
    private Boolean truthValue;
    private String allowedRevealModes;
    
    // === CONSTRUCTORS ===
    
    public FactEstablishment() {}
    
    public FactEstablishment(String statement, Boolean truthValue, String allowedRevealModes) {
        this.statement = statement;
        this.truthValue = truthValue;
        this.allowedRevealModes = allowedRevealModes;
    }
    
    // === GETTERS AND SETTERS ===
    
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
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if this fact has reveal restrictions
     */
    public boolean isRestricted() {
        return allowedRevealModes != null && !allowedRevealModes.isEmpty();
    }
    
    /**
     * Check if this fact is publicly observable
     */
    public boolean isPublic() {
        return allowedRevealModes == null || allowedRevealModes.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("FactEstablishment[%s: %s]", 
            isRestricted() ? "RESTRICTED" : "PUBLIC", 
            statement);
    }
}