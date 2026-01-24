package com.github.rrousso.erik_core.entities;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure data class holding all the details for a Stanza.
 * Parsing logic is handled by StanzaExtractorService.
 * 
 * ENHANCED: Now separates public role (visible to characters) from private backstory (narrator-only)
 */
public class StanzaMetadata {
    
    private List<String> characters = new ArrayList<>();
    private String setting = "";
    private String premise = "";
    private String userRole = "";  // PUBLIC - what characters can observe
    private String userBackstory = "";  // PRIVATE - narrator-only, characters don't know
    private String tone = "";
    private List<String> previousEvents = new ArrayList<>();
    private List<String> specialRules = new ArrayList<>();
    
    // ========== GETTERS ==========
    
    public List<String> getCharacters() {
        return characters;
    }
    
    public String getSetting() {
        return setting;
    }
    
    public String getPremise() {
        return premise;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public String getUserBackstory() {
        return userBackstory;
    }
    
    public String getTone() {
        return tone;
    }
    
    public List<String> getPreviousEvents() {
        return previousEvents;
    }
    
    public List<String> getSpecialRules() {
        return specialRules;
    }
    
    // ========== SETTERS ==========
    
    public void setCharacters(List<String> characters) {
        this.characters = characters;
    }
    
    public void setSetting(String setting) {
        this.setting = setting;
    }
    
    public void setPremise(String premise) {
        this.premise = premise;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    public void setUserBackstory(String userBackstory) {
        this.userBackstory = userBackstory;
    }
    
    public void setTone(String tone) {
        this.tone = tone;
    }
    
    public void setPreviousEvents(List<String> previousEvents) {
        this.previousEvents = previousEvents;
    }
    
    public void setSpecialRules(List<String> specialRules) {
        this.specialRules = specialRules;
    }
    
    // ========== PRESENTATION ==========
    
    /**
     * Convert to a narrative-friendly string for the narrator system prompt
     * 
     * ENHANCED: Now includes BOTH public context (for characters) and private context (narrator-only)
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("CURRENT STANZA SETUP:\n\n");
        
        // PUBLIC INFORMATION - Characters can observe/infer this
        sb.append("=== PUBLIC CONTEXT (characters can know this) ===\n\n");
        
        if (!setting.isEmpty()) {
            sb.append("Setting: ").append(setting).append("\n\n");
        }
        
        if (!premise.isEmpty()) {
            sb.append("Premise: ").append(premise).append("\n\n");
        }
        
        if (!userRole.isEmpty()) {
            sb.append("User's Observable Role: ").append(userRole).append("\n\n");
        }
        
        if (!characters.isEmpty()) {
            sb.append("Characters Present:\n");
            for (String character : characters) {
                sb.append("- ").append(character).append("\n");
            }
            sb.append("\n");
        }
        
        if (!tone.isEmpty()) {
            sb.append("Tone/Genre: ").append(tone).append("\n\n");
        }
        
        if (!previousEvents.isEmpty()) {
            sb.append("=== WHAT HAPPENED PREVIOUSLY ===\n\n");
            for (int i = 0; i < previousEvents.size(); i++) {
                sb.append((i + 1) + ". " + previousEvents.get(i) + "\n");
            }
            sb.append("\n");
        }
        
        if (!specialRules.isEmpty()) {
            sb.append("Special Rules:\n");
            for (String rule : specialRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }
        
        // PRIVATE INFORMATION - Only narrator knows, characters don't
        if (!userBackstory.isEmpty()) {
            sb.append("=== PRIVATE CONTEXT (NARRATOR-ONLY - characters do NOT know this) ===\n\n");
            sb.append("User's Backstory (hidden from characters):\n");
            sb.append(userBackstory).append("\n\n");
            sb.append("CRITICAL: This information is PRIVATE. Characters cannot know, sense, or infer this ");
            sb.append("unless the user explicitly reveals it in dialogue or actions. ");
            sb.append("Characters are NOT psychic. They can only know what they observe or are told.\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Debug helper
     */
    public void printDebug() {
        System.out.println("\n[DEBUG] StanzaMetadata:");
        System.out.println("  Setting: " + setting);
        System.out.println("  Premise: " + premise);
        System.out.println("  User Role (PUBLIC): " + userRole);
        System.out.println("  User Backstory (PRIVATE): " + userBackstory);
        System.out.println("  Tone: " + tone);
        System.out.println("  Characters: " + characters);
        System.out.println("  Special Rules: " + specialRules);
    }
}