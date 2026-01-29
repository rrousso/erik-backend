package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Represents a secret - a lock on a fact that prevents characters from learning it
 * unless explicitly unlocked.
 * 
 * Secrets define:
 * - Which fact is locked
 * - Whether it can be inferred (or only directly revealed)
 * - Which reveal modes are valid (TOLD, OBSERVED, SENSED_SPECIAL, etc.)
 * 
 * Java enforces: if a character tries to learn a locked fact through an invalid
 * reveal mode, the update is rejected.
 */
@Entity
@Table(name = "stanza_secrets")
public class Secret {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fact_id", nullable = false)
    private Fact fact;
    
    /**
     * Can characters figure this out through inference?
     * If false, only direct reveal modes (TOLD, OBSERVED, etc.) work.
     * If true, INFERRED is also valid.
     */
    @Column(nullable = false)
    private boolean inferable = false;
    
    /**
     * Comma-separated list of valid reveal modes.
     * Options: TOLD, OBSERVED, DOCUMENTED, INFERRED, SENSED_SPECIAL
     * 
     * Example: "TOLD" - can only learn if explicitly told
     * Example: "TOLD,OBSERVED,SENSED_SPECIAL" - multiple valid modes
     */
    @Column(name = "allowed_reveal_modes", nullable = false, length = 200)
    private String allowedRevealModes = "TOLD";
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    // === CONSTRUCTORS ===
    
    public Secret() {}
    
    public Secret(Stanza stanza, Fact fact) {
        this.stanza = stanza;
        this.fact = fact;
    }
    
    public Secret(Stanza stanza, Fact fact, boolean inferable, String allowedRevealModes) {
        this.stanza = stanza;
        this.fact = fact;
        this.inferable = inferable;
        this.allowedRevealModes = allowedRevealModes;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if a reveal mode is valid for this secret
     */
    public boolean isRevealModeAllowed(String mode) {
        if (mode == null || allowedRevealModes == null) {
            return false;
        }
        
        // Special case: INFERRED requires inferable=true
        if ("INFERRED".equals(mode) && !inferable) {
            return false;
        }
        
        String[] modes = allowedRevealModes.split(",");
        for (String allowed : modes) {
            if (allowed.trim().equalsIgnoreCase(mode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get allowed modes as array
     */
    public String[] getAllowedModesArray() {
        if (allowedRevealModes == null || allowedRevealModes.isEmpty()) {
            return new String[0];
        }
        return allowedRevealModes.split(",");
    }
    
    /**
     * Check if this secret can only be learned by being told
     */
    public boolean isToldOnly() {
        return "TOLD".equals(allowedRevealModes);
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
    
    public Fact getFact() {
        return fact;
    }
    
    public void setFact(Fact fact) {
        this.fact = fact;
    }
    
    public boolean isInferable() {
        return inferable;
    }
    
    public void setInferable(boolean inferable) {
        this.inferable = inferable;
    }
    
    public String getAllowedRevealModes() {
        return allowedRevealModes;
    }
    
    public void setAllowedRevealModes(String allowedRevealModes) {
        this.allowedRevealModes = allowedRevealModes;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
