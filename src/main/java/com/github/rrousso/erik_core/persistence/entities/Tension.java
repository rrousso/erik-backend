package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a narrative tension - a tracked story thread that can surface,
 * escalate, or resolve.
 * 
 * Tensions are used to:
 * - Guide which characters become "potential" for a scene
 * - Help architect decide what's emotionally at stake
 * - Track story progress over multiple exchanges
 * 
 * Pressure scale (1-10):
 * - 1-3: Background, could become relevant
 * - 4-6: Present, will probably surface
 * - 7-9: Imminent, likely to surface soon
 * - 10: Explosive, will surface this exchange
 */
@Entity
@Table(name = "stanza_tensions")
public class Tension {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    // === TENSION IDENTITY ===
    
    /**
     * Brief description of the tension.
     * Example: "Stiles' desperation for relief from nogitsune trauma"
     */
    @Column(nullable = false, length = 500)
    private String description;
    
    /**
     * Characters involved in this tension.
     * Comma-separated names.
     */
    @Column(name = "involved_characters", length = 500)
    private String involvedCharacters;
    
    // === PRESSURE ===
    
    /**
     * Current pressure level (1-10).
     * Higher pressure = more likely to surface.
     */
    @Column(nullable = false)
    private Integer pressure = 5;
    
    /**
     * Things that could make this tension surface or escalate.
     * Stored as text for flexibility.
     */
    @Column(name = "potential_triggers", length = 1000)
    private String potentialTriggers;
    
    // === SOURCE & STATUS ===
    
    /**
     * Where did this tension come from?
     * USER_BACKSTORY - derived from user's private backstory
     * CHARACTER_DYNAMIC - from character relationships
     * WORLD_STATE - from world circumstances
     * USER_STATED - user explicitly mentioned it
     * NARRATOR_EMERGENT - emerged from narration
     */
    @Column(length = 30)
    private String source;
    
    /**
     * Current status.
     * ACTIVE - ongoing tension
     * RESOLVED - tension has been addressed
     * DORMANT - temporarily inactive
     */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    
    // === TRACKING ===
    
    /**
     * Beat when this tension was created.
     */
    @Column(name = "created_beat")
    private Integer createdBeat;
    
    /**
     * Beat when this tension was last updated.
     */
    @Column(name = "updated_beat")
    private Integer updatedBeat;
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public Tension() {}
    
    public Tension(Stanza stanza, String description, int pressure) {
        this.stanza = stanza;
        this.description = description;
        this.pressure = pressure;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isResolved() {
        return "RESOLVED".equals(status);
    }
    
    public boolean isDormant() {
        return "DORMANT".equals(status);
    }
    
    public boolean isHighPressure() {
        return pressure >= 7;
    }
    
    public boolean isMediumPressure() {
        return pressure >= 4 && pressure < 7;
    }
    
    public boolean isLowPressure() {
        return pressure < 4;
    }
    
    /**
     * Escalate tension by amount (max 10)
     */
    public void escalate(int amount) {
        this.pressure = Math.min(10, this.pressure + amount);
    }
    
    /**
     * De-escalate tension by amount (min 1)
     */
    public void deescalate(int amount) {
        this.pressure = Math.max(1, this.pressure - amount);
    }
    
    /**
     * Mark as resolved
     */
    public void resolve() {
        this.status = "RESOLVED";
    }
    
    /**
     * Mark as dormant
     */
    public void makeDormant() {
        this.status = "DORMANT";
    }
    
    /**
     * Reactivate a dormant tension
     */
    public void reactivate() {
        this.status = "ACTIVE";
    }
    
    /**
     * Check if a character is involved in this tension
     */
    public boolean involvesCharacter(String characterName) {
        if (involvedCharacters == null || characterName == null) {
            return false;
        }
        String[] names = involvedCharacters.split(",");
        for (String name : names) {
            if (name.trim().equalsIgnoreCase(characterName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get involved characters as array
     */
    public String[] getInvolvedCharactersArray() {
        if (involvedCharacters == null || involvedCharacters.isEmpty()) {
            return new String[0];
        }
        return involvedCharacters.split(",");
    }
    
    /**
     * Get potential triggers as array
     */
    public String[] getPotentialTriggersArray() {
        if (potentialTriggers == null || potentialTriggers.isEmpty()) {
            return new String[0];
        }
        return potentialTriggers.split("\\|");  // Use pipe as delimiter for triggers
    }
    
    // === GETTERS AND SETTERS ===
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Stanza getStanza() {
        return stanza;
    }
    
    public void setStanza(Stanza stanza) {
        this.stanza = stanza;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getInvolvedCharacters() {
        return involvedCharacters;
    }
    
    public void setInvolvedCharacters(String involvedCharacters) {
        this.involvedCharacters = involvedCharacters;
    }
    
    public Integer getPressure() {
        return pressure;
    }
    
    public void setPressure(Integer pressure) {
        this.pressure = Math.max(1, Math.min(10, pressure));
    }
    
    public String getPotentialTriggers() {
        return potentialTriggers;
    }
    
    public void setPotentialTriggers(String potentialTriggers) {
        this.potentialTriggers = potentialTriggers;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getCreatedBeat() {
        return createdBeat;
    }
    
    public void setCreatedBeat(Integer createdBeat) {
        this.createdBeat = createdBeat;
    }
    
    public Integer getUpdatedBeat() {
        return updatedBeat;
    }
    
    public void setUpdatedBeat(Integer updatedBeat) {
        this.updatedBeat = updatedBeat;
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
}
