package com.github.rrousso.erik_core.dto.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing an extracted event from the narrative.
 * 
 * Maps to the JSON structure:
 * {
 *   "description": "brief description of what happened",
 *   "significance": "MAJOR" | "MINOR" | "DETAIL",
 *   "charactersInvolved": ["character name", "another name"]
 * }
 * 
 * Events are atomic things that happened in the narrative that should
 * be recorded in the database for future reference.
 */
public class EventExtraction {
    
    private String description;
    private String significance;  // MAJOR, MINOR, DETAIL
    private List<String> charactersInvolved = new ArrayList<>();
    
    // === CONSTRUCTORS ===
    
    public EventExtraction() {}
    
    public EventExtraction(String description, String significance) {
        this.description = description;
        this.significance = significance;
    }
    
    // === GETTERS AND SETTERS ===
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getSignificance() {
        return significance;
    }
    
    public void setSignificance(String significance) {
        this.significance = significance;
    }
    
    public List<String> getCharactersInvolved() {
        return charactersInvolved;
    }
    
    public void setCharactersInvolved(List<String> charactersInvolved) {
        this.charactersInvolved = charactersInvolved != null ? charactersInvolved : new ArrayList<>();
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if this is a major event
     */
    public boolean isMajor() {
        return "MAJOR".equalsIgnoreCase(significance);
    }
    
    /**
     * Check if this is a minor event
     */
    public boolean isMinor() {
        return "MINOR".equalsIgnoreCase(significance);
    }
    
    /**
     * Check if this is a detail
     */
    public boolean isDetail() {
        return "DETAIL".equalsIgnoreCase(significance);
    }
    
    @Override
    public String toString() {
        return String.format("EventExtraction[%s: %s]", significance, description);
    }
}