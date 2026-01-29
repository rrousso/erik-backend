package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a narrative tension - a tracked story thread that can surface, escalate, or resolve.
 * 
 * Tensions are used by the architect to:
 * - Decide which characters might appear (high-pressure tensions make involved characters "potential")
 * - Guide the narrator on what's emotionally at stake
 * - Track story progress over multiple beats
 * 
 * Pressure scale:
 * - 1-3: Background tension, could become relevant
 * - 4-6: Present tension, will probably surface
 * - 7-9: Imminent tension, likely to surface soon
 * - 10: Explosive, will surface this beat
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NarrativeTension {
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("involvedCharacters")
    private List<String> involvedCharacters = new ArrayList<>();
    
    @JsonProperty("pressure")
    private int pressure = 5;
    
    @JsonProperty("potentialTriggers")
    private List<String> potentialTriggers = new ArrayList<>();
    
    @JsonProperty("source")
    private String source; // USER_BACKSTORY | CHARACTER_DYNAMIC | WORLD_STATE | USER_STATED
    
    // Runtime tracking (not from JSON)
    private String status = "ACTIVE"; // ACTIVE | RESOLVED | DORMANT
    
    // ========== CONSTRUCTORS ==========
    
    public NarrativeTension() {}
    
    public NarrativeTension(String description, List<String> involvedCharacters, int pressure) {
        this.description = description;
        this.involvedCharacters = involvedCharacters;
        this.pressure = pressure;
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    public boolean isHighPressure() {
        return pressure >= 7;
    }
    
    public boolean isMediumPressure() {
        return pressure >= 4 && pressure < 7;
    }
    
    public boolean isLowPressure() {
        return pressure < 4;
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isResolved() {
        return "RESOLVED".equals(status);
    }
    
    public boolean involvesCharacter(String characterName) {
        return involvedCharacters.stream()
            .anyMatch(c -> c.equalsIgnoreCase(characterName));
    }
    
    /**
     * Escalate tension by amount (max 10)
     */
    public void escalate(int amount) {
        this.pressure = Math.min(10, this.pressure + amount);
    }
    
    /**
     * De-escalate tension by amount (min 1)
     */
    public void deescalate(int amount) {
        this.pressure = Math.max(1, this.pressure - amount);
    }
    
    /**
     * Mark tension as resolved
     */
    public void resolve() {
        this.status = "RESOLVED";
    }
    
    /**
     * Mark tension as dormant (not active but not resolved)
     */
    public void makeDormant() {
        this.status = "DORMANT";
    }
    
    // ========== FORMAT FOR NARRATOR ==========
    
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        // Pressure indicator
        String pressureLabel;
        if (pressure >= 9) {
            pressureLabel = "🔴 CRITICAL";
        } else if (pressure >= 7) {
            pressureLabel = "🟠 HIGH";
        } else if (pressure >= 4) {
            pressureLabel = "🟡 MEDIUM";
        } else {
            pressureLabel = "🟢 LOW";
        }
        
        sb.append("[").append(pressureLabel).append(" - ").append(pressure).append("/10] ");
        sb.append(description).append("\n");
        
        if (!involvedCharacters.isEmpty()) {
            sb.append("  Involves: ").append(String.join(", ", involvedCharacters)).append("\n");
        }
        
        if (!potentialTriggers.isEmpty()) {
            sb.append("  Could surface through: ");
            sb.append(String.join("; ", potentialTriggers.subList(0, Math.min(2, potentialTriggers.size()))));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getInvolvedCharacters() {
        return involvedCharacters;
    }
    
    public void setInvolvedCharacters(List<String> involvedCharacters) {
        this.involvedCharacters = involvedCharacters;
    }
    
    public int getPressure() {
        return pressure;
    }
    
    public void setPressure(int pressure) {
        this.pressure = Math.max(1, Math.min(10, pressure));
    }
    
    public List<String> getPotentialTriggers() {
        return potentialTriggers;
    }
    
    public void setPotentialTriggers(List<String> potentialTriggers) {
        this.potentialTriggers = potentialTriggers;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}