package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Main stanza container - the living state of a narrative simulation.
 * 
 * This is NOT a snapshot saved at the end - it's the active container
 * that gets updated every exchange throughout the stanza's lifetime.
 */
@Entity
@Table(name = "stanzas")
public class Stanza {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;
    
    // === WORLD IDENTIFIER ===
    @Column(name = "world_identifier", length = 100)
    private String worldIdentifier;  // "teen_wolf", "marvel", "original", etc.
    
    // === STATUS ===
    @Column(length = 20)
    private String status = "active";  // active, paused, completed, abandoned
    
    // === WORLD CONTEXT (rarely changes) ===
    @Column(name = "time_context", length = 500)
    private String timeContext;
    
    @Column(name = "world_state", length = 1000)
    private String worldState;
    
    @Column(name = "world_rules", columnDefinition = "TEXT[]")
    private String[] worldRules;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String locations;  // JSONB array of {name, description, whoMightBeThere}
    
    // === SEARCH FIELDS (for /search command) ===
    @Column(length = 500)
    private String setting;
    
    @Column(length = 1000)
    private String premise;
    
    @Column(length = 200)
    private String tone;
    
    @Column(name = "quick_synopsis", length = 2000)
    private String quickSynopsis;
    
    // Note: search_vector will be added via SQL migration for full-text search
    // PostgreSQL tsvector type isn't directly supported by Hibernate
    
    // === TRACKING ===
    @Column(name = "current_beat")
    private Integer currentBeat = 0;
    
    @Column(name = "current_exchange")
    private Integer currentExchange = 0;
    
    // === RELATIONSHIPS ===
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaCharacter> characters = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Fact> facts = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Secret> secrets = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tension> tensions = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaEvent> events = new ArrayList<>();
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public Stanza() {}
    
    public Stanza(Persona persona, String worldIdentifier) {
        this.persona = persona;
        this.worldIdentifier = worldIdentifier;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isActive() {
        return "active".equals(status);
    }
    
    public boolean isPaused() {
        return "paused".equals(status);
    }
    
    public boolean isCompleted() {
        return "completed".equals(status);
    }
    
    public boolean isAbandoned() {
        return "abandoned".equals(status);
    }
    
    public void incrementExchange() {
        this.currentExchange++;
    }
    
    public void incrementBeat() {
        this.currentBeat++;
        this.currentExchange++;
    }
    
    public StanzaCharacter getUserCharacter() {
        return characters.stream()
            .filter(StanzaCharacter::isUser)
            .findFirst()
            .orElse(null);
    }
    
    public List<StanzaCharacter> getPresentCharacters() {
        return characters.stream()
            .filter(c -> "present".equals(c.getPresenceStatus()))
            .toList();
    }
    
    public List<StanzaCharacter> getPotentialCharacters() {
        return characters.stream()
            .filter(c -> "potential".equals(c.getPresenceStatus()))
            .toList();
    }
    
    public List<Tension> getActiveTensions() {
        return tensions.stream()
            .filter(t -> "active".equals(t.getStatus()))
            .toList();
    }
    
    public List<Tension> getHighPressureTensions() {
        return tensions.stream()
            .filter(t -> "active".equals(t.getStatus()) && t.getPressure() >= 7)
            .toList();
    }
    
    // === GETTERS AND SETTERS ===
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Persona getPersona() {
        return persona;
    }
    
    public void setPersona(Persona persona) {
        this.persona = persona;
    }
    
    public String getWorldIdentifier() {
        return worldIdentifier;
    }
    
    public void setWorldIdentifier(String worldIdentifier) {
        this.worldIdentifier = worldIdentifier;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getTimeContext() {
        return timeContext;
    }
    
    public void setTimeContext(String timeContext) {
        this.timeContext = timeContext;
    }
    
    public String getWorldState() {
        return worldState;
    }
    
    public void setWorldState(String worldState) {
        this.worldState = worldState;
    }
    
    public String[] getWorldRules() {
        return worldRules;
    }
    
    public void setWorldRules(String[] worldRules) {
        this.worldRules = worldRules;
    }
    
    public String getLocations() {
        return locations;
    }
    
    public void setLocations(String locations) {
        this.locations = locations;
    }
    
    public String getSetting() {
        return setting;
    }
    
    public void setSetting(String setting) {
        this.setting = setting;
    }
    
    public String getPremise() {
        return premise;
    }
    
    public void setPremise(String premise) {
        this.premise = premise;
    }
    
    public String getTone() {
        return tone;
    }
    
    public void setTone(String tone) {
        this.tone = tone;
    }
    
    public String getQuickSynopsis() {
        return quickSynopsis;
    }
    
    public void setQuickSynopsis(String quickSynopsis) {
        this.quickSynopsis = quickSynopsis;
    }
    
    public Integer getCurrentBeat() {
        return currentBeat;
    }
    
    public void setCurrentBeat(Integer currentBeat) {
        this.currentBeat = currentBeat;
    }
    
    public Integer getCurrentExchange() {
        return currentExchange;
    }
    
    public void setCurrentExchange(Integer currentExchange) {
        this.currentExchange = currentExchange;
    }
    
    public List<StanzaCharacter> getCharacters() {
        return characters;
    }
    
    public void setCharacters(List<StanzaCharacter> characters) {
        this.characters = characters;
    }
    
    public List<Fact> getFacts() {
        return facts;
    }
    
    public void setFacts(List<Fact> facts) {
        this.facts = facts;
    }
    
    public List<Secret> getSecrets() {
        return secrets;
    }
    
    public void setSecrets(List<Secret> secrets) {
        this.secrets = secrets;
    }
    
    public List<Tension> getTensions() {
        return tensions;
    }
    
    public void setTensions(List<Tension> tensions) {
        this.tensions = tensions;
    }
    
    public List<StanzaEvent> getEvents() {
        return events;
    }
    
    public void setEvents(List<StanzaEvent> events) {
        this.events = events;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // ========== FORMAT FOR NARRATOR ==========
    
    /**
     * Convert to a narrator-friendly context string.
     * This is what gets injected into the narrator's system prompt.
     * Mirrors InitializedStanza.toNarratorContext() but reads from DB fields.
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== STANZA INITIALIZATION ===\n\n");
        
        // World identifier
        if (worldIdentifier != null && !worldIdentifier.isEmpty()) {
            sb.append("World: ").append(worldIdentifier.toUpperCase()).append("\n\n");
        }
        
        // User character
        StanzaCharacter user = getUserCharacter();
        if (user != null) {
            sb.append("=== USER CHARACTER ===\n\n");
            sb.append(formatUserCharacter(user));
            sb.append("\n");
        }
        
        // Present characters (full context)
        List<StanzaCharacter> present = getPresentCharacters();
        // Filter out user from present list
        present = present.stream().filter(c -> !c.isUser()).toList();
        if (!present.isEmpty()) {
            sb.append("=== CHARACTERS IN SCENE (Full Context) ===\n\n");
            for (StanzaCharacter c : present) {
                sb.append(formatCharacterFull(c));
                sb.append("\n---\n\n");
            }
        }
        
        // Potential characters (limited context)
        List<StanzaCharacter> potential = getPotentialCharacters();
        if (!potential.isEmpty()) {
            sb.append("=== CHARACTERS WHO MIGHT APPEAR ===\n");
            sb.append("(You MAY introduce these if narratively appropriate)\n\n");
            for (StanzaCharacter c : potential) {
                sb.append(formatCharacterPotential(c));
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // Background characters (reference only)
        List<StanzaCharacter> background = getBackgroundCharacters();
        if (!background.isEmpty()) {
            sb.append("=== BACKGROUND CHARACTERS (Reference Only) ===\n");
            sb.append("(May be mentioned in dialogue, should NOT appear without setup)\n\n");
            for (StanzaCharacter c : background) {
                sb.append("- ").append(c.getName());
                if (c.getCanonRole() != null) {
                    sb.append(" (").append(c.getCanonRole()).append(")");
                }
                if (c.getEmotionalState() != null) {
                    // Background chars store threat/ally info in emotionalState field
                    sb.append(" - ").append(c.getEmotionalState());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // Active tensions
        List<Tension> activeTensions = getActiveTensions();
        if (!activeTensions.isEmpty()) {
            sb.append("=== ACTIVE NARRATIVE TENSIONS ===\n\n");
            for (Tension t : activeTensions) {
                sb.append(formatTension(t));
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // World context
        sb.append("=== WORLD CONTEXT ===\n\n");
        sb.append(formatWorldContext());
        
        return sb.toString();
    }
    
    /**
     * Get background characters
     */
    public List<StanzaCharacter> getBackgroundCharacters() {
        return characters.stream()
            .filter(c -> "background".equals(c.getPresenceStatus()))
            .toList();
    }
    
    // ========== FORMATTING HELPERS ==========
    
    private String formatUserCharacter(StanzaCharacter user) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(user.getName()).append("**\n");
        
        if (user.getPublicRole() != null && !user.getPublicRole().isEmpty()) {
            sb.append("Public Role: ").append(user.getPublicRole()).append("\n");
        }
        
        if (user.getPrivateBackstory() != null && !user.getPrivateBackstory().isEmpty()) {
            sb.append("Private Backstory (NARRATOR ONLY): ").append(user.getPrivateBackstory()).append("\n");
        }
        
        if (user.getCurrentLocation() != null && !user.getCurrentLocation().isEmpty()) {
            sb.append("Current Location: ").append(user.getCurrentLocation()).append("\n");
        }
        
        if (user.getVisibleTraits() != null && user.getVisibleTraits().length > 0) {
            sb.append("Visible Traits: ").append(String.join(", ", user.getVisibleTraits())).append("\n");
        }
        
        if (user.getGoals() != null && user.getGoals().length > 0) {
            sb.append("Current Goals: ").append(String.join(", ", user.getGoals())).append("\n");
        }
        
        return sb.toString();
    }
    
    private String formatCharacterFull(StanzaCharacter c) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(c.getName()).append("**\n");
        
        if (c.getCanonRole() != null && !c.getCanonRole().isEmpty()) {
            sb.append("Canon Role: ").append(c.getCanonRole()).append("\n");
        }
        
        if (c.getEmotionalState() != null && !c.getEmotionalState().isEmpty()) {
            sb.append("Emotional State: ").append(c.getEmotionalState()).append("\n");
        }
        
        if (c.getRelationshipToUser() != null && !c.getRelationshipToUser().isEmpty()) {
            sb.append("Relationship to User: ").append(c.getRelationshipToUser()).append("\n");
        }
        
        if (c.getMotivations() != null && c.getMotivations().length > 0) {
            sb.append("Motivations: ").append(String.join(", ", c.getMotivations())).append("\n");
        }
        
        // Format what they know
        if (!c.getKnownFacts().isEmpty()) {
            sb.append("Currently Knows:\n");
            for (CharacterKnowledge k : c.getKnownFacts()) {
                sb.append("  - ").append(k.getFact().getPredicate()).append("\n");
            }
        }
        
        // Format what they don't know (secrets they're unaware of)
        List<CharacterSecretState> unaware = c.getSecretStates().stream()
            .filter(s -> "UNAWARE".equals(s.getState()))
            .toList();
        if (!unaware.isEmpty()) {
            sb.append("Does NOT Know:\n");
            for (CharacterSecretState s : unaware) {
                sb.append("  - ").append(s.getSecret().getFact().getPredicate()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    private String formatCharacterPotential(StanzaCharacter c) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(c.getName()).append("**");
        
        if (c.getCanonRole() != null && !c.getCanonRole().isEmpty()) {
            sb.append(" (").append(c.getCanonRole()).append(")");
        }
        
        if (c.getRelationshipToUser() != null && !c.getRelationshipToUser().isEmpty()) {
            sb.append(" - ").append(c.getRelationshipToUser());
        }
        
        return sb.toString();
    }
    
    private String formatTension(Tension t) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(t.getDescription()).append("**\n");
        sb.append("  Pressure: ").append(t.getPressure()).append("/10\n");
        
        if (t.getInvolvedCharacters() != null && !t.getInvolvedCharacters().isEmpty()) {
            sb.append("  Involved: ").append(t.getInvolvedCharacters()).append("\n");
        }
        
        if (t.getPotentialTriggers() != null && !t.getPotentialTriggers().isEmpty()) {
            sb.append("  Triggers: ").append(t.getPotentialTriggers().replace("|", ", ")).append("\n");
        }
        
        return sb.toString();
    }
    
    private String formatWorldContext() {
        StringBuilder sb = new StringBuilder();
        
        if (timeContext != null && !timeContext.isEmpty()) {
            sb.append("Time: ").append(timeContext).append("\n");
        }
        
        if (worldState != null && !worldState.isEmpty()) {
            sb.append("Current State: ").append(worldState).append("\n");
        }
        
        if (worldRules != null && worldRules.length > 0) {
            sb.append("Rules:\n");
            for (String rule : worldRules) {
                sb.append("  - ").append(rule).append("\n");
            }
        }
        
        return sb.toString();
    }
}