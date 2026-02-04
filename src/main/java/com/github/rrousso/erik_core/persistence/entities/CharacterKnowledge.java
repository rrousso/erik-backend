package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tracks a character's knowledge state for a specific fact.
 * 
 * This is the single source of truth for "what does this character know?"
 * 
 * Combines two concepts:
 * 1. Awareness: Does the character know this fact exists? (UNAWARE/SUSPICIOUS/KNOWS)
 * 2. Learning: How and when did they learn it?
 * 
 * States:
 * - UNAWARE: Character has no idea about this fact (record exists to track restricted facts)
 * - SUSPICIOUS: Character suspects something but doesn't know the full truth
 * - KNOWS: Character knows this fact
 * 
 * The presence of a CharacterKnowledge record means:
 * - Either the character KNOWS the fact (awarenessState = KNOWS)
 * - Or the fact has restricted discovery rules and we're tracking their awareness
 * 
 * Absence of a record means:
 * - The fact is publicly observable (no restrictions)
 * - Or we haven't tracked it yet
 */
@Entity
@Table(name = "character_knowledge",
       uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "fact_id"}))
public class CharacterKnowledge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private StanzaCharacter character;
    
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fact_id", nullable = false)
    private Fact fact;
    
    // === AWARENESS STATE ===
    
    /**
     * Character's awareness of this fact.
     * 
     * KNOWS - Character knows this fact is true
     * SUSPICIOUS - Character suspects something but doesn't know the truth
     * UNAWARE - Character has no idea (only tracked for restricted facts)
     * 
     * This field determines whether the character can act on this information.
     */
    @Column(name = "awareness_state", nullable = false, length = 20)
    private String awarenessState = "KNOWS";
    
    // === LEARNING DETAILS ===
    
    /**
     * How did they learn/become aware of this?
     * 
     * TOLD - Someone told them
     * OBSERVED - They saw it happen
     * DOCUMENTED - They read it somewhere
     * INFERRED - They figured it out from other facts
     * SENSED_SPECIAL - Special ability revealed it (magic, werewolf senses, etc.)
     * 
     * This must match one of the fact's allowedRevealModes (if restricted).
     */
    @Column(nullable = false, length = 30)
    private String how;
    
    /**
     * Status of this knowledge.
     * 
     * LEARNED - They know it's true (fact's factValue is true)
     * BELIEVED - They think it's true but might be wrong (fact's factValue is false)
     * 
     * This allows tracking misinformation/false beliefs.
     */
    @Column(nullable = false, length = 20)
    private String status = "LEARNED";
    
    /**
     * Optional: Evidence facts that led to this knowledge.
     * Comma-separated fact keys.
     * 
     * Particularly relevant for INFERRED learning - what facts did they combine?
     */
    @Column(name = "evidence_facts", length = 500)
    private String evidenceFacts;
    
    /**
     * Which beat this knowledge was acquired/updated.
     */
    @Column(name = "learned_beat")
    private Integer learnedBeat;
    
    /**
     * Which exchange this knowledge was acquired/updated.
     */
    @Column(name = "learned_exchange")
    private Integer learnedExchange;
    
    // === TIMESTAMPS ===
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public CharacterKnowledge() {}
    
    /**
     * Constructor for when character learns a fact (KNOWS state).
     */
    public CharacterKnowledge(StanzaCharacter character, Fact fact, String how) {
        this.character = character;
        this.fact = fact;
        this.how = how;
        this.awarenessState = "KNOWS";
        this.status = "LEARNED";
    }
    
    /**
     * Constructor for tracking awareness state (including UNAWARE/SUSPICIOUS).
     */
    public CharacterKnowledge(StanzaCharacter character, Fact fact, String awarenessState, String how) {
        this.character = character;
        this.fact = fact;
        this.awarenessState = awarenessState;
        this.how = how;
    }
    
    // === CONVENIENCE METHODS ===
    
    // Awareness state checks
    
    public boolean isUnaware() {
        return "UNAWARE".equals(awarenessState);
    }
    
    public boolean isSuspicious() {
        return "SUSPICIOUS".equals(awarenessState);
    }
    
    public boolean knows() {
        return "KNOWS".equals(awarenessState);
    }
    
    /**
     * Can this character act on this fact?
     * Only true if they KNOW it.
     */
    public boolean canActOnFact() {
        return "KNOWS".equals(awarenessState);
    }
    
    // Learning method checks
    
    public boolean wasInferred() {
        return "INFERRED".equals(how);
    }
    
    public boolean wasTold() {
        return "TOLD".equals(how);
    }
    
    public boolean wasObserved() {
        return "OBSERVED".equals(how);
    }
    
    public boolean wasSensedSpecial() {
        return "SENSED_SPECIAL".equals(how);
    }
    
    // Knowledge status checks
    
    public boolean isLearned() {
        return "LEARNED".equals(status);
    }
    
    public boolean isBelieved() {
        return "BELIEVED".equals(status);
    }
    
    // State transitions
    
    /**
     * Unlock this knowledge (set awareness to KNOWS).
     */
    public void unlock(String how, Integer beat, Integer exchange) {
        this.awarenessState = "KNOWS";
        this.how = truncateHow(how);
        this.learnedBeat = beat;
        this.learnedExchange = exchange;
    }
    
    /**
     * Make character suspicious about this fact.
     */
    public void makeSuspicious(String how, Integer beat, Integer exchange) {
        this.awarenessState = "SUSPICIOUS";
        this.how = truncateHow(how);
        this.learnedBeat = beat;
        this.learnedExchange = exchange;
    }
    
    /**
     * Truncate 'how' value to fit database constraint (30 chars max).
     */
    private String truncateHow(String how) {
        if (how == null) {
            return "OBSERVED"; // Default
        }
        if (how.length() > 30) {
            String truncated = how.substring(0, 30);
            // Try to avoid cutting mid-word by finding last space
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > 15) { // Only use space if it's not too early
                truncated = truncated.substring(0, lastSpace);
            }
            return truncated;
        }
        return how;
    }
    
    // Evidence handling
    
    /**
     * Get evidence facts as array.
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
    
    public Fact getFact() {
        return fact;
    }
    
    public void setFact(Fact fact) {
        this.fact = fact;
    }
    
    public String getAwarenessState() {
        return awarenessState;
    }
    
    public void setAwarenessState(String awarenessState) {
        this.awarenessState = awarenessState;
    }
    
    public String getHow() {
        return how;
    }
    
    public void setHow(String how) {
        this.how = truncateHow(how);
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getEvidenceFacts() {
        return evidenceFacts;
    }
    
    public void setEvidenceFacts(String evidenceFacts) {
        this.evidenceFacts = evidenceFacts;
    }
    
    public Integer getLearnedBeat() {
        return learnedBeat;
    }
    
    public void setLearnedBeat(Integer learnedBeat) {
        this.learnedBeat = learnedBeat;
    }
    
    public Integer getLearnedExchange() {
        return learnedExchange;
    }
    
    public void setLearnedExchange(Integer learnedExchange) {
        this.learnedExchange = learnedExchange;
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