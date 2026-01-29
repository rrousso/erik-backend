package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the user's character in the stanza.
 * Separates public (observable) information from private (narrator-only) backstory.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCharacter {
    
    @JsonProperty("publicRole")
    private String publicRole;
    
    @JsonProperty("privateBackstory")
    private String privateBackstory;
    
    @JsonProperty("currentLocation")
    private String currentLocation;
    
    @JsonProperty("currentGoals")
    private List<String> currentGoals = new ArrayList<>();
    
    @JsonProperty("knownFacts")
    private List<String> knownFacts = new ArrayList<>();
    
    @JsonProperty("publiclyVisibleTraits")
    private List<String> publiclyVisibleTraits = new ArrayList<>();
    
    // ========== CONSTRUCTORS ==========
    
    public UserCharacter() {}
    
    // ========== FORMAT FOR NARRATOR ==========
    
    /**
     * Convert to narrator context string
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        // Public role (what characters can see)
        sb.append("**PUBLIC ROLE (What characters can observe):**\n");
        if (publicRole != null && !publicRole.isEmpty()) {
            sb.append(publicRole).append("\n");
        }
        sb.append("\n");
        
        // Visible traits
        if (!publiclyVisibleTraits.isEmpty()) {
            sb.append("**Visible Traits:**\n");
            for (String trait : publiclyVisibleTraits) {
                sb.append("- ").append(trait).append("\n");
            }
            sb.append("\n");
        }
        
        // Current location
        if (currentLocation != null && !currentLocation.isEmpty()) {
            sb.append("**Current Location:** ").append(currentLocation).append("\n\n");
        }
        
        // Current goals
        if (!currentGoals.isEmpty()) {
            sb.append("**Current Goals:**\n");
            for (String goal : currentGoals) {
                sb.append("- ").append(goal).append("\n");
            }
            sb.append("\n");
        }
        
        // What the user character knows
        if (!knownFacts.isEmpty()) {
            sb.append("**User Knows:**\n");
            for (String fact : knownFacts) {
                sb.append("- ").append(fact).append("\n");
            }
            sb.append("\n");
        }
        
        // Private backstory (NARRATOR ONLY)
        if (privateBackstory != null && !privateBackstory.isEmpty()) {
            sb.append("**PRIVATE BACKSTORY (Narrator-only, characters do NOT know this):**\n");
            sb.append(privateBackstory).append("\n");
            sb.append("\nCRITICAL: This information is SECRET. Characters cannot know, sense, or infer ");
            sb.append("any of this unless the user explicitly reveals it through dialogue or actions.\n");
        }
        
        return sb.toString();
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public String getPublicRole() {
        return publicRole;
    }
    
    public void setPublicRole(String publicRole) {
        this.publicRole = publicRole;
    }
    
    public String getPrivateBackstory() {
        return privateBackstory;
    }
    
    public void setPrivateBackstory(String privateBackstory) {
        this.privateBackstory = privateBackstory;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public List<String> getCurrentGoals() {
        return currentGoals;
    }
    
    public void setCurrentGoals(List<String> currentGoals) {
        this.currentGoals = currentGoals;
    }
    
    public List<String> getKnownFacts() {
        return knownFacts;
    }
    
    public void setKnownFacts(List<String> knownFacts) {
        this.knownFacts = knownFacts;
    }
    
    public List<String> getPubliclyVisibleTraits() {
        return publiclyVisibleTraits;
    }
    
    public void setPubliclyVisibleTraits(List<String> publiclyVisibleTraits) {
        this.publiclyVisibleTraits = publiclyVisibleTraits;
    }
}