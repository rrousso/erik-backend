package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a character in the stanza with full knowledge tracking.
 * Used for both explicit (user-mentioned) and likely (inferred) characters.
 * 
 * Key innovation: Explicit tracking of what characters KNOW and DON'T KNOW
 * to prevent information bleed in narration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StanzaCharacter {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("canonRole")
    private String canonRole;
    
    @JsonProperty("presentInFirstScene")
    private boolean presentInFirstScene;
    
    @JsonProperty("currentKnowledge")
    private List<String> currentKnowledge = new ArrayList<>();
    
    @JsonProperty("doesNotKnow")
    private List<String> doesNotKnow = new ArrayList<>();
    
    @JsonProperty("currentEmotionalState")
    private String currentEmotionalState;
    
    @JsonProperty("currentMotivations")
    private List<String> currentMotivations = new ArrayList<>();
    
    @JsonProperty("relationshipToUser")
    private String relationshipToUser;
    
    @JsonProperty("whyIncluded")
    private String whyIncluded;
    
    // ========== CONSTRUCTORS ==========
    
    public StanzaCharacter() {}
    
    // ========== FORMAT FOR NARRATOR ==========
    
    /**
     * Full context for present characters
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("**").append(name.toUpperCase()).append("**\n");
        
        if (canonRole != null && !canonRole.isEmpty()) {
            sb.append("Role: ").append(canonRole).append("\n");
        }
        
        if (relationshipToUser != null && !relationshipToUser.isEmpty()) {
            sb.append("Relationship to User: ").append(relationshipToUser).append("\n");
        }
        
        if (currentEmotionalState != null && !currentEmotionalState.isEmpty()) {
            sb.append("Current Emotional State: ").append(currentEmotionalState).append("\n");
        }
        
        if (!currentMotivations.isEmpty()) {
            sb.append("Current Motivations:\n");
            for (String motivation : currentMotivations) {
                sb.append("  - ").append(motivation).append("\n");
            }
        }
        
        // CRITICAL: Knowledge boundaries
        if (!currentKnowledge.isEmpty()) {
            sb.append("\n**KNOWS:**\n");
            for (String fact : currentKnowledge) {
                sb.append("  ✓ ").append(fact).append("\n");
            }
        }
        
        if (!doesNotKnow.isEmpty()) {
            sb.append("\n**DOES NOT KNOW (Cannot act on this information):**\n");
            for (String fact : doesNotKnow) {
                sb.append("  ✗ ").append(fact).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Limited context for potential characters (might appear)
     */
    public String toPotentialContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("- **").append(name).append("**");
        
        if (canonRole != null && !canonRole.isEmpty()) {
            sb.append(" (").append(canonRole).append(")");
        }
        
        sb.append("\n");
        
        if (whyIncluded != null && !whyIncluded.isEmpty()) {
            sb.append("  Why they might appear: ").append(whyIncluded).append("\n");
        }
        
        if (currentEmotionalState != null && !currentEmotionalState.isEmpty()) {
            sb.append("  Current state: ").append(currentEmotionalState).append("\n");
        }
        
        if (!currentKnowledge.isEmpty()) {
            sb.append("  If they appear, they know: ");
            sb.append(String.join("; ", currentKnowledge.subList(0, Math.min(3, currentKnowledge.size()))));
            sb.append("\n");
        }
        
        if (!doesNotKnow.isEmpty()) {
            sb.append("  They do NOT know: ");
            sb.append(String.join("; ", doesNotKnow.subList(0, Math.min(2, doesNotKnow.size()))));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    // ========== KNOWLEDGE MANAGEMENT ==========
    
    /**
     * Add a fact to this character's knowledge
     */
    public void learnFact(String fact) {
        if (!currentKnowledge.contains(fact)) {
            currentKnowledge.add(fact);
        }
        // Remove from doesNotKnow if present
        doesNotKnow.remove(fact);
    }
    
    /**
     * Check if character knows a specific fact
     */
    public boolean knows(String fact) {
        return currentKnowledge.stream()
            .anyMatch(k -> k.toLowerCase().contains(fact.toLowerCase()));
    }
    
    /**
     * Check if character explicitly doesn't know something
     */
    public boolean explicitlyDoesNotKnow(String fact) {
        return doesNotKnow.stream()
            .anyMatch(k -> k.toLowerCase().contains(fact.toLowerCase()));
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCanonRole() {
        return canonRole;
    }
    
    public void setCanonRole(String canonRole) {
        this.canonRole = canonRole;
    }
    
    public boolean isPresentInFirstScene() {
        return presentInFirstScene;
    }
    
    public void setPresentInFirstScene(boolean presentInFirstScene) {
        this.presentInFirstScene = presentInFirstScene;
    }
    
    public List<String> getCurrentKnowledge() {
        return currentKnowledge;
    }
    
    public void setCurrentKnowledge(List<String> currentKnowledge) {
        this.currentKnowledge = currentKnowledge;
    }
    
    public List<String> getDoesNotKnow() {
        return doesNotKnow;
    }
    
    public void setDoesNotKnow(List<String> doesNotKnow) {
        this.doesNotKnow = doesNotKnow;
    }
    
    public String getCurrentEmotionalState() {
        return currentEmotionalState;
    }
    
    public void setCurrentEmotionalState(String currentEmotionalState) {
        this.currentEmotionalState = currentEmotionalState;
    }
    
    public List<String> getCurrentMotivations() {
        return currentMotivations;
    }
    
    public void setCurrentMotivations(List<String> currentMotivations) {
        this.currentMotivations = currentMotivations;
    }
    
    public String getRelationshipToUser() {
        return relationshipToUser;
    }
    
    public void setRelationshipToUser(String relationshipToUser) {
        this.relationshipToUser = relationshipToUser;
    }
    
    public String getWhyIncluded() {
        return whyIncluded;
    }
    
    public void setWhyIncluded(String whyIncluded) {
        this.whyIncluded = whyIncluded;
    }
}