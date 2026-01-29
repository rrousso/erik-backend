package com.github.rrousso.erik_core.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete initialized state of a stanza.
 * This is the parsed output from the Initialization Architect call.
 * 
 * Replaces StanzaMetadata with a much richer structure that includes:
 * - Character knowledge boundaries
 * - Tiered character presence
 * - Narrative tensions
 * - World context
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializedStanza {
    
    @JsonProperty("worldIdentifier")
    private String worldIdentifier;
    
    @JsonProperty("userCharacter")
    private UserCharacter userCharacter;
    
    @JsonProperty("explicitCharacters")
    private List<StanzaCharacter> explicitCharacters = new ArrayList<>();
    
    @JsonProperty("likelyCharacters")
    private List<StanzaCharacter> likelyCharacters = new ArrayList<>();
    
    @JsonProperty("backgroundCharacters")
    private List<BackgroundCharacter> backgroundCharacters = new ArrayList<>();
    
    @JsonProperty("initialTensions")
    private List<NarrativeTension> initialTensions = new ArrayList<>();
    
    @JsonProperty("worldContext")
    private WorldContext worldContext;
    
    @JsonProperty("clarificationsNeeded")
    private List<String> clarificationsNeeded = new ArrayList<>();
    
    // ========== CONSTRUCTORS ==========
    
    public InitializedStanza() {}
    
    // ========== CONVENIENCE METHODS ==========
    
    /**
     * Get all characters who should be present in the first scene
     */
    public List<StanzaCharacter> getFirstSceneCharacters() {
        List<StanzaCharacter> present = new ArrayList<>();
        for (StanzaCharacter c : explicitCharacters) {
            if (c.isPresentInFirstScene()) {
                present.add(c);
            }
        }
        for (StanzaCharacter c : likelyCharacters) {
            if (c.isPresentInFirstScene()) {
                present.add(c);
            }
        }
        return present;
    }
    
    /**
     * Get characters who could potentially appear (not present but relevant)
     */
    public List<StanzaCharacter> getPotentialCharacters() {
        List<StanzaCharacter> potential = new ArrayList<>();
        for (StanzaCharacter c : explicitCharacters) {
            if (!c.isPresentInFirstScene()) {
                potential.add(c);
            }
        }
        for (StanzaCharacter c : likelyCharacters) {
            if (!c.isPresentInFirstScene()) {
                potential.add(c);
            }
        }
        return potential;
    }
    
    /**
     * Get high-pressure tensions (7+)
     */
    public List<NarrativeTension> getHighPressureTensions() {
        List<NarrativeTension> high = new ArrayList<>();
        for (NarrativeTension t : initialTensions) {
            if (t.getPressure() >= 7) {
                high.add(t);
            }
        }
        return high;
    }
    
    /**
     * Find a character by name across all tiers
     */
    public StanzaCharacter findCharacterByName(String name) {
        for (StanzaCharacter c : explicitCharacters) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        for (StanzaCharacter c : likelyCharacters) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }
    
    /**
     * Check if this is a known IP or original world
     */
    public boolean isKnownIP() {
        return worldIdentifier != null && !worldIdentifier.equalsIgnoreCase("original");
    }
    
    /**
     * Check if there are clarifications needed before starting
     */
    public boolean needsClarification() {
        return clarificationsNeeded != null && !clarificationsNeeded.isEmpty();
    }
    
    // ========== FORMAT FOR NARRATOR ==========
    
    /**
     * Convert to a narrator-friendly context string.
     * This is what gets injected into the narrator's system prompt.
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== STANZA INITIALIZATION ===\n\n");
        
        // World identifier
        if (worldIdentifier != null && !worldIdentifier.isEmpty()) {
            sb.append("World: ").append(worldIdentifier.toUpperCase()).append("\n\n");
        }
        
        // User character
        if (userCharacter != null) {
            sb.append("=== USER CHARACTER ===\n\n");
            sb.append(userCharacter.toNarratorContext());
            sb.append("\n");
        }
        
        // Present characters (full context)
        List<StanzaCharacter> present = getFirstSceneCharacters();
        if (!present.isEmpty()) {
            sb.append("=== CHARACTERS IN SCENE (Full Context) ===\n\n");
            for (StanzaCharacter c : present) {
                sb.append(c.toNarratorContext());
                sb.append("\n---\n\n");
            }
        }
        
        // Potential characters (limited context)
        List<StanzaCharacter> potential = getPotentialCharacters();
        if (!potential.isEmpty()) {
            sb.append("=== CHARACTERS WHO MIGHT APPEAR ===\n");
            sb.append("(You MAY introduce these if narratively appropriate)\n\n");
            for (StanzaCharacter c : potential) {
                sb.append(c.toPotentialContext());
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // Background characters (reference only)
        if (!backgroundCharacters.isEmpty()) {
            sb.append("=== BACKGROUND CHARACTERS (Reference Only) ===\n");
            sb.append("(May be mentioned in dialogue, should NOT appear without setup)\n\n");
            for (BackgroundCharacter c : backgroundCharacters) {
                sb.append("- ").append(c.getName());
                sb.append(" (").append(c.getCanonRole()).append(")");
                sb.append(" - ").append(c.getThreatOrAlly()).append("\n");
            }
            sb.append("\n");
        }
        
        // Active tensions
        if (!initialTensions.isEmpty()) {
            sb.append("=== ACTIVE NARRATIVE TENSIONS ===\n\n");
            for (NarrativeTension t : initialTensions) {
                sb.append(t.toNarratorContext());
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // World context
        if (worldContext != null) {
            sb.append("=== WORLD CONTEXT ===\n\n");
            sb.append(worldContext.toNarratorContext());
        }
        
        return sb.toString();
    }
    
    // ========== GETTERS AND SETTERS ==========
    
    public String getWorldIdentifier() {
        return worldIdentifier;
    }
    
    public void setWorldIdentifier(String worldIdentifier) {
        this.worldIdentifier = worldIdentifier;
    }
    
    public UserCharacter getUserCharacter() {
        return userCharacter;
    }
    
    public void setUserCharacter(UserCharacter userCharacter) {
        this.userCharacter = userCharacter;
    }
    
    public List<StanzaCharacter> getExplicitCharacters() {
        return explicitCharacters;
    }
    
    public void setExplicitCharacters(List<StanzaCharacter> explicitCharacters) {
        this.explicitCharacters = explicitCharacters;
    }
    
    public List<StanzaCharacter> getLikelyCharacters() {
        return likelyCharacters;
    }
    
    public void setLikelyCharacters(List<StanzaCharacter> likelyCharacters) {
        this.likelyCharacters = likelyCharacters;
    }
    
    public List<BackgroundCharacter> getBackgroundCharacters() {
        return backgroundCharacters;
    }
    
    public void setBackgroundCharacters(List<BackgroundCharacter> backgroundCharacters) {
        this.backgroundCharacters = backgroundCharacters;
    }
    
    public List<NarrativeTension> getInitialTensions() {
        return initialTensions;
    }
    
    public void setInitialTensions(List<NarrativeTension> initialTensions) {
        this.initialTensions = initialTensions;
    }
    
    public WorldContext getWorldContext() {
        return worldContext;
    }
    
    public void setWorldContext(WorldContext worldContext) {
        this.worldContext = worldContext;
    }
    
    public List<String> getClarificationsNeeded() {
        return clarificationsNeeded;
    }
    
    public void setClarificationsNeeded(List<String> clarificationsNeeded) {
        this.clarificationsNeeded = clarificationsNeeded;
    }
}