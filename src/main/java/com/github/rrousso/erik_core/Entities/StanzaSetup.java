package com.github.rrousso.erik_core.entities;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure data class holding all the details for a Stanza.
 * Parsing logic is handled by StanzaExtractorService.
 */
public class StanzaSetup {
    
    private List<String> characters = new ArrayList<>();
    private String setting = "";
    private String premise = "";
    private String userRole = "";
    private String tone = "";
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
    
    public String getTone() {
        return tone;
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
    
    public void setTone(String tone) {
        this.tone = tone;
    }
    
    public void setSpecialRules(List<String> specialRules) {
        this.specialRules = specialRules;
    }
    
    // ========== PRESENTATION ==========
    
    /**
     * Convert to a narrative-friendly string for the narrator system prompt
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("CURRENT STANZA SETUP:\n\n");
        
        if (!setting.isEmpty()) {
            sb.append("Setting: ").append(setting).append("\n\n");
        }
        
        if (!premise.isEmpty()) {
            sb.append("Premise: ").append(premise).append("\n\n");
        }
        
        if (!userRole.isEmpty()) {
            sb.append("User's Role: ").append(userRole).append("\n\n");
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
        
        if (!specialRules.isEmpty()) {
            sb.append("Special Rules:\n");
            for (String rule : specialRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Debug helper
     */
    public void printDebug() {
        System.out.println("\n[DEBUG] StanzaSetup:");
        System.out.println("  Setting: " + setting);
        System.out.println("  Premise: " + premise);
        System.out.println("  User Role: " + userRole);
        System.out.println("  Tone: " + tone);
        System.out.println("  Characters: " + characters);
        System.out.println("  Special Rules: " + specialRules);
    }
}