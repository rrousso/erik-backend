package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the world context for a stanza.
 * Includes supernatural rules, current world state, and relevant locations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldContext {
    
    @JsonProperty("supernaturalRules")
    private List<String> supernaturalRules = new ArrayList<>();
    
    @JsonProperty("currentWorldState")
    private String currentWorldState;
    
    @JsonProperty("relevantLocations")
    private List<RelevantLocation> relevantLocations = new ArrayList<>();
    
    @JsonProperty("timeContext")
    private String timeContext;
    
    // ========== CONSTRUCTORS ==========
    
    public WorldContext() {}
    
    // ========== INNER CLASS ==========
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelevantLocation {
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("whoMightBeThere")
        private List<String> whoMightBeThere = new ArrayList<>();
        
        public RelevantLocation() {}
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public List<String> getWhoMightBeThere() {
            return whoMightBeThere;
        }
        
        public void setWhoMightBeThere(List<String> whoMightBeThere) {
            this.whoMightBeThere = whoMightBeThere;
        }
        
        public String toNarratorContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("- **").append(name).append("**: ").append(description);
            if (!whoMightBeThere.isEmpty()) {
                sb.append(" (Might find: ").append(String.join(", ", whoMightBeThere)).append(")");
            }
            return sb.toString();
        }
    }
    
    // ========== FORMAT FOR NARRATOR ==========
    
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        // Time context
        if (timeContext != null && !timeContext.isEmpty()) {
            sb.append("**When:** ").append(timeContext).append("\n\n");
        }
        
        // World state
        if (currentWorldState != null && !currentWorldState.isEmpty()) {
            sb.append("**Current World State:**\n").append(currentWorldState).append("\n\n");
        }
        
        // Supernatural rules
        if (!supernaturalRules.isEmpty()) {
            sb.append("**World Rules:**\n");
            for (String rule : supernaturalRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }
        
        // Locations
        if (!relevantLocations.isEmpty()) {
            sb.append("**Known Locations:**\n");
            for (RelevantLocation loc : relevantLocations) {
                sb.append(loc.toNarratorContext()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    /**
     * Find a location by name
     */
    public RelevantLocation findLocation(String name) {
        return relevantLocations.stream()
            .filter(l -> l.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get all location names
     */
    public List<String> getLocationNames() {
        return relevantLocations.stream()
            .map(RelevantLocation::getName)
            .toList();
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public List<String> getSupernaturalRules() {
        return supernaturalRules;
    }
    
    public void setSupernaturalRules(List<String> supernaturalRules) {
        this.supernaturalRules = supernaturalRules;
    }
    
    public String getCurrentWorldState() {
        return currentWorldState;
    }
    
    public void setCurrentWorldState(String currentWorldState) {
        this.currentWorldState = currentWorldState;
    }
    
    public List<RelevantLocation> getRelevantLocations() {
        return relevantLocations;
    }
    
    public void setRelevantLocations(List<RelevantLocation> relevantLocations) {
        this.relevantLocations = relevantLocations;
    }
    
    public String getTimeContext() {
        return timeContext;
    }
    
    public void setTimeContext(String timeContext) {
        this.timeContext = timeContext;
    }
}