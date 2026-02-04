package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a fact as defined during initialization.
 * 
 * The LLM provides:
 * - tempId: Temporary ID for referencing (e.g., "fact_1", "fact_2")
 * - statement: The actual fact content (e.g., "User is a Spark")
 * - truthValue: Whether this fact is true or false
 * - allowedRevealModes: How this fact can be learned (null = public/observable)
 * 
 * Characters then reference facts by tempId in their "knows" lists.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitFact {
    
    @JsonProperty("tempId")
    private String tempId;
    
    @JsonProperty("statement")
    private String statement;
    
    @JsonProperty("truthValue")
    private Boolean truthValue;
    
    @JsonProperty("allowedRevealModes")
    private String allowedRevealModes;  // Comma-separated: "TOLD,OBSERVED,INFERRED"
    
    // === CONSTRUCTORS ===
    
    public InitFact() {}
    
    public InitFact(String tempId, String statement, Boolean truthValue, String allowedRevealModes) {
        this.tempId = tempId;
        this.statement = statement;
        this.truthValue = truthValue;
        this.allowedRevealModes = allowedRevealModes;
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
    
    /**
     * Check if this is a restricted fact (has reveal mode constraints).
     */
    public boolean isRestricted() {
        return allowedRevealModes != null && !allowedRevealModes.isEmpty();
    }
    
    /**
     * Check if this is a public/observable fact.
     */
    public boolean isPublic() {
        return allowedRevealModes == null || allowedRevealModes.isEmpty();
    }
}