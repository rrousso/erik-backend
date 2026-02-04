package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a character in the initialization phase.
 * Updated to match Gemini's blueprint-based format.
 * 
 * Key changes from previous format:
 * - Added blueprint (tier1/tier2/tier3 structure)
 * - Replaced currentKnowledge/doesNotKnow with knows array (fact tempIds)
 * - Removed presentInFirstScene (determined by narrator context)
 * - Removed whyIncluded (less important in practice)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StanzaCharacter {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("canonRole")
    private String canonRole;
    
    @JsonProperty("knows")
    private List<String> knows = new ArrayList<>();  // References to fact tempIds
    
    @JsonProperty("currentEmotionalState")
    private String currentEmotionalState;
    
    @JsonProperty("relationshipToUser")
    private String relationshipToUser;

    @JsonProperty("presentInFirstScene")
    private Boolean presentInFirstScene;  

    @JsonProperty("blueprint")
    private CharacterBlueprint blueprint;
    
    // ========== CONSTRUCTORS ==========
    
    public StanzaCharacter() {}
    
    // ========== INNER CLASS: BLUEPRINT ==========
    
    /**
     * Three-tiered character definition structure.
     * This gives the narrator essential character info in a compact format.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterBlueprint {
        
        @JsonProperty("tier1_essentials")
        private String tier1Essentials;  // Archetype & Speech Pattern
        
        @JsonProperty("tier2_motivators")
        private String tier2Motivators;  // Primary Goal & Major Fear
        
        @JsonProperty("tier3_anchors")
        private List<String> tier3Anchors = new ArrayList<>();  // 3 visual details
        
        public CharacterBlueprint() {}
        
        public String getTier1Essentials() {
            return tier1Essentials;
        }
        
        public void setTier1Essentials(String tier1Essentials) {
            this.tier1Essentials = tier1Essentials;
        }
        
        public String getTier2Motivators() {
            return tier2Motivators;
        }
        
        public void setTier2Motivators(String tier2Motivators) {
            this.tier2Motivators = tier2Motivators;
        }
        
        public List<String> getTier3Anchors() {
            return tier3Anchors;
        }
        
        public void setTier3Anchors(List<String> tier3Anchors) {
            this.tier3Anchors = tier3Anchors;
        }
        
        /**
         * Format blueprint for narrator context
         */
        public String toNarratorContext() {
            StringBuilder sb = new StringBuilder();
            
            if (tier1Essentials != null && !tier1Essentials.isEmpty()) {
                sb.append("Essentials: ").append(tier1Essentials).append("\n");
            }
            if (tier2Motivators != null && !tier2Motivators.isEmpty()) {
                sb.append("Motivators: ").append(tier2Motivators).append("\n");
            }
            if (tier3Anchors != null && !tier3Anchors.isEmpty()) {
                sb.append("Visual Anchors: ").append(String.join(", ", tier3Anchors)).append("\n");
            }
            
            return sb.toString();
        }
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    /**
     * Check if this character should be in the opening scene
     */
    public boolean isPresentInFirstScene() {
        return presentInFirstScene != null && presentInFirstScene;
    }
    
    
    /**
     * Format for narrator context with full detail
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
            sb.append("Current State: ").append(currentEmotionalState).append("\n");
        }
        
        if (blueprint != null) {
            sb.append("\n").append(blueprint.toNarratorContext());
        }
        
        // Knowledge will be added separately by persistence layer
        
        return sb.toString();
    }
    
    /**
     * Format for potential character (less detail)
     */
    public String toPotentialContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(name);
        if (canonRole != null && !canonRole.isEmpty()) {
            sb.append(" (").append(canonRole).append(")");
        }
        return sb.toString();
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
    
    public List<String> getKnows() {
        return knows;
    }
    
    public void setKnows(List<String> knows) {
        this.knows = knows;
    }
    
    public String getCurrentEmotionalState() {
        return currentEmotionalState;
    }
    
    public void setCurrentEmotionalState(String currentEmotionalState) {
        this.currentEmotionalState = currentEmotionalState;
    }
    
    public String getRelationshipToUser() {
        return relationshipToUser;
    }
    
    public void setRelationshipToUser(String relationshipToUser) {
        this.relationshipToUser = relationshipToUser;
    }
    
    public Boolean getPresentInFirstScene() {
        return presentInFirstScene;
    }

    public void setPresentInFirstScene(Boolean presentInFirstScene) {
        this.presentInFirstScene = presentInFirstScene;
    }
    
    public CharacterBlueprint getBlueprint() {
        return blueprint;
    }
    
    public void setBlueprint(CharacterBlueprint blueprint) {
        this.blueprint = blueprint;
    }
}