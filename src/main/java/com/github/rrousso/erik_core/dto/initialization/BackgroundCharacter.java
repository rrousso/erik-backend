package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight representation of a background character.
 * These characters exist in the world but aren't expected to appear soon.
 * 
 * Used for:
 * - Reference in dialogue ("Did you hear about Peter?")
 * - Potential future relevance
 * - World-building texture
 * 
 * Should NOT be introduced into scenes without significant setup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackgroundCharacter {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("canonRole")
    private String canonRole;
    
    @JsonProperty("relevanceToStanza")
    private String relevanceToStanza;
    
    @JsonProperty("threatOrAlly")
    private String threatOrAlly; // THREAT | ALLY | NEUTRAL | UNKNOWN
    
    // ========== CONSTRUCTORS ==========
    
    public BackgroundCharacter() {}
    
    public BackgroundCharacter(String name, String canonRole, String relevanceToStanza, String threatOrAlly) {
        this.name = name;
        this.canonRole = canonRole;
        this.relevanceToStanza = relevanceToStanza;
        this.threatOrAlly = threatOrAlly;
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    public boolean isThreat() {
        return "THREAT".equalsIgnoreCase(threatOrAlly);
    }
    
    public boolean isAlly() {
        return "ALLY".equalsIgnoreCase(threatOrAlly);
    }
    
    public boolean isUnknown() {
        return "UNKNOWN".equalsIgnoreCase(threatOrAlly);
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
    
    public String getRelevanceToStanza() {
        return relevanceToStanza;
    }
    
    public void setRelevanceToStanza(String relevanceToStanza) {
        this.relevanceToStanza = relevanceToStanza;
    }
    
    public String getThreatOrAlly() {
        return threatOrAlly;
    }
    
    public void setThreatOrAlly(String threatOrAlly) {
        this.threatOrAlly = threatOrAlly;
    }
}
