package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Represents an event - something that happened in the stanza.
 * 
 * Events are append-only and limited to 280 characters (like old tweets).
 * If something needs more description, it should be split into multiple events.
 * 
 * Events provide:
 * - Chronological record of what happened
 * - Context for synopsis generation
 * - Reference for continuation
 */
@Entity
@Table(name = "stanza_events")
public class StanzaEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    /**
     * What happened. Max 280 characters.
     * Should be one atomic event.
     * Example: "Derek sensed user's magical signature at the fair"
     */
    @Column(nullable = false, length = 280)
    private String description;
    
    /**
     * Which beat this event occurred in.
     */
    @Column(name = "beat_number")
    private Integer beatNumber;
    
    /**
     * Which exchange this event occurred in.
     */
    @Column(name = "exchange_number")
    private Integer exchangeNumber;
    
    /**
     * Optional: characters involved in this event.
     * Comma-separated names.
     */
    @Column(name = "involved_characters", length = 300)
    private String involvedCharacters;
    
    /**
     * Is this a major event? Major events are prioritized in synopsis.
     */
    @Column(name = "is_major")
    private boolean isMajor = false;
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    // === CONSTRUCTORS ===
    
    public StanzaEvent() {}
    
    public StanzaEvent(Stanza stanza, String description) {
        this.stanza = stanza;
        this.description = truncateDescription(description);
    }
    
    public StanzaEvent(Stanza stanza, String description, Integer beatNumber, Integer exchangeNumber) {
        this.stanza = stanza;
        this.description = truncateDescription(description);
        this.beatNumber = beatNumber;
        this.exchangeNumber = exchangeNumber;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Ensure description is max 280 chars
     */
    private String truncateDescription(String desc) {
        if (desc == null) return "";
        if (desc.length() <= 280) return desc;
        return desc.substring(0, 277) + "...";
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
     * Check if a character was involved in this event
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
        this.description = truncateDescription(description);
    }
    
    public Integer getBeatNumber() {
        return beatNumber;
    }
    
    public void setBeatNumber(Integer beatNumber) {
        this.beatNumber = beatNumber;
    }
    
    public Integer getExchangeNumber() {
        return exchangeNumber;
    }
    
    public void setExchangeNumber(Integer exchangeNumber) {
        this.exchangeNumber = exchangeNumber;
    }
    
    public String getInvolvedCharacters() {
        return involvedCharacters;
    }
    
    public void setInvolvedCharacters(String involvedCharacters) {
        this.involvedCharacters = involvedCharacters;
    }
    
    public boolean isMajor() {
        return isMajor;
    }
    
    public void setMajor(boolean isMajor) {
        this.isMajor = isMajor;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
