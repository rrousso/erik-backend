package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Tracks which facts a character has learned.
 * 
 * This is the "knows" relationship - if a CharacterKnowledge record exists,
 * the character knows that fact.
 * 
 * Absence of a record means the character doesn't know the fact.
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
    
    /**
     * How did they learn this?
     * TOLD - someone told them
     * OBSERVED - they saw it happen
     * DOCUMENTED - they read it somewhere
     * INFERRED - they figured it out from other facts
     * SENSED_SPECIAL - special ability revealed it (magic, werewolf senses, etc.)
     */
    @Column(nullable = false, length = 30)
    private String how;
    
    /**
     * Status of this knowledge.
     * LEARNED - they know it's true
     * BELIEVED - they think it's true (might be wrong)
     */
    @Column(nullable = false, length = 20)
    private String status = "LEARNED";
    
    /**
     * Optional: fact IDs that served as evidence for INFERRED knowledge.
     * Stored as comma-separated fact keys.
     */
    @Column(name = "evidence_facts", length = 500)
    private String evidenceFacts;
    
    /**
     * Which beat this was learned in.
     */
    @Column(name = "learned_beat")
    private Integer learnedBeat;
    
    /**
     * Which exchange this was learned in.
     */
    @Column(name = "learned_exchange")
    private Integer learnedExchange;
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    // === CONSTRUCTORS ===
    
    public CharacterKnowledge() {}
    
    public CharacterKnowledge(StanzaCharacter character, Fact fact, String how) {
        this.character = character;
        this.fact = fact;
        this.how = how;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isLearned() {
        return "LEARNED".equals(status);
    }
    
    public boolean isBelieved() {
        return "BELIEVED".equals(status);
    }
    
    public boolean wasInferred() {
        return "INFERRED".equals(how);
    }
    
    public boolean wasTold() {
        return "TOLD".equals(how);
    }
    
    public boolean wasObserved() {
        return "OBSERVED".equals(how);
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
    
    public Fact getFact() {
        return fact;
    }
    
    public void setFact(Fact fact) {
        this.fact = fact;
    }
    
    public String getHow() {
        return how;
    }
    
    public void setHow(String how) {
        this.how = how;
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
}
