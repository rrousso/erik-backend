package com.github.rrousso.erik_core.dto.extraction;

/**
 * DTO representing a tension change - when a story thread escalates or resolves.
 * 
 * Maps to the JSON structure:
 * {
 *   "tensionDescription": "match from active tensions",
 *   "changeType": "ESCALATED" | "DE_ESCALATED" | "RESOLVED" | "CREATED",
 *   "newPressure": 5,
 *   "reason": "what caused the change"
 * }
 * 
 * This will be used to update Tension entries in the database,
 * adjusting pressure levels or marking tensions as resolved.
 */
public class TensionChange {
    
    private String tensionDescription;
    private String changeType;  // ESCALATED, DE_ESCALATED, RESOLVED, CREATED
    private Integer newPressure;
    private String reason;
    
    // === CONSTRUCTORS ===
    
    public TensionChange() {}
    
    public TensionChange(String tensionDescription, String changeType, Integer newPressure, String reason) {
        this.tensionDescription = tensionDescription;
        this.changeType = changeType;
        this.newPressure = newPressure;
        this.reason = reason;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getTensionDescription() {
        return tensionDescription;
    }
    
    public void setTensionDescription(String tensionDescription) {
        this.tensionDescription = tensionDescription;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public Integer getNewPressure() {
        return newPressure;
    }
    
    public void setNewPressure(Integer newPressure) {
        this.newPressure = newPressure;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if tension escalated
     */
    public boolean isEscalated() {
        return "ESCALATED".equalsIgnoreCase(changeType);
    }
    
    /**
     * Check if tension de-escalated
     */
    public boolean isDeEscalated() {
        return "DE_ESCALATED".equalsIgnoreCase(changeType);
    }
    
    /**
     * Check if tension resolved
     */
    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(changeType);
    }
    
    /**
     * Check if this is a new tension
     */
    public boolean isCreated() {
        return "CREATED".equalsIgnoreCase(changeType);
    }
    
    @Override
    public String toString() {
        return String.format("TensionChange[%s: %s → pressure %d]", 
            changeType, tensionDescription, newPressure);
    }
}