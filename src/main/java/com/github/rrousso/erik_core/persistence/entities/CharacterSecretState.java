package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tracks a character's state regarding a specific secret.
 * 
 * States:
 * - UNAWARE: Has no clue about this secret
 * - SUSPICIOUS: Something feels off, but doesn't know what
 * - KNOWS: The secret is unlocked, they can now learn the underlying fact
 * 
 * This is the "lock" mechanism. Even if the architect says "Derek learns user_is_spark",
 * Java checks: does Derek have KNOWS state for the secret guarding that fact?
 * If not, the update is rejected.
 */
@Entity
@Table(name = "character_secret_states",
       uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "secret_id"}))
public class CharacterSecretState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private StanzaCharacter character;
    
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "secret_id", nullable = false)
    private Secret secret;
    
    /**
     * Current state: UNAWARE, SUSPICIOUS, KNOWS
     */
    @Column(nullable = false, length = 20)
    private String state = "UNAWARE";
    
    /**
     * How did they reach this state? (Only relevant for SUSPICIOUS or KNOWS)
     * TOLD, OBSERVED, DOCUMENTED, INFERRED, SENSED_SPECIAL
     */
    @Column(length = 30)
    private String how;
    
    /**
     * Optional: evidence facts that led to this state change.
     * Comma-separated fact keys.
     */
    @Column(name = "evidence_facts", length = 500)
    private String evidenceFacts;
    
    /**
     * Which beat this state was last updated.
     */
    @Column(name = "updated_beat")
    private Integer updatedBeat;
    
    /**
     * Which exchange this state was last updated.
     */
    @Column(name = "updated_exchange")
    private Integer updatedExchange;
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public CharacterSecretState() {}
    
    public CharacterSecretState(StanzaCharacter character, Secret secret) {
        this.character = character;
        this.secret = secret;
        this.state = "UNAWARE";
    }
    
    public CharacterSecretState(StanzaCharacter character, Secret secret, String state, String how) {
        this.character = character;
        this.secret = secret;
        this.state = state;
        this.how = how;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isUnaware() {
        return "UNAWARE".equals(state);
    }
    
    public boolean isSuspicious() {
        return "SUSPICIOUS".equals(state);
    }
    
    public boolean knows() {
        return "KNOWS".equals(state);
    }
    
    /**
     * Check if character can learn the fact guarded by this secret
     */
    public boolean canLearnFact() {
        return "KNOWS".equals(state);
    }
    
    /**
     * Unlock this secret (set state to KNOWS)
     */
    public void unlock(String how, Integer beat, Integer exchange) {
        this.state = "KNOWS";
        this.how = how;
        this.updatedBeat = beat;
        this.updatedExchange = exchange;
    }
    
    /**
     * Make character suspicious
     */
    public void makeSuspicious(String how, Integer beat, Integer exchange) {
        this.state = "SUSPICIOUS";
        this.how = how;
        this.updatedBeat = beat;
        this.updatedExchange = exchange;
    }
    
    /**
     * Get evidence facts as array
     */
    public String[] getEvidenceFactsArray() {
        if (evidenceFacts == null || evidenceFacts.isEmpty()) {
            return new String[0];
        }
        return evidenceFacts.split(",");
    }
    
    // === GETTERS AND SETTERS ===
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public StanzaCharacter getCharacter() {
        return character;
    }
    
    public void setCharacter(StanzaCharacter character) {
        this.character = character;
    }
    
    public Secret getSecret() {
        return secret;
    }
    
    public void setSecret(Secret secret) {
        this.secret = secret;
    }
    
    public String getState() {
        return state;
    }
    
    public void setState(String state) {
        this.state = state;
    }
    
    public String getHow() {
        return how;
    }
    
    public void setHow(String how) {
        this.how = how;
    }
    
    public String getEvidenceFacts() {
        return evidenceFacts;
    }
    
    public void setEvidenceFacts(String evidenceFacts) {
        this.evidenceFacts = evidenceFacts;
    }
    
    public Integer getUpdatedBeat() {
        return updatedBeat;
    }
    
    public void setUpdatedBeat(Integer updatedBeat) {
        this.updatedBeat = updatedBeat;
    }
    
    public Integer getUpdatedExchange() {
        return updatedExchange;
    }
    
    public void setUpdatedExchange(Integer updatedExchange) {
        this.updatedExchange = updatedExchange;
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
