package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Represents a beat (scene) in a stanza.
 * 
 * Beats are user-controlled narrative boundaries - like scenes in a screenplay
 * or chapters in a book. They mark significant transitions in location, time,
 * or perspective.
 * 
 * Beat lifecycle:
 * 1. Created when user types ((next beat: context))
 * 2. Remains active (endExchange = null) while exchanges happen
 * 3. Ends when next beat starts or stanza ends
 * 4. Summary generated when beat ends (from all events in beat)
 * 5. Minor events deleted after summary is created
 */
@Entity
@Table(name = "beats")
public class Beat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    @Column(name = "beat_number", nullable = false)
    private Integer beatNumber;
    
    // === EXCHANGE BOUNDARIES ===
    
    @Column(name = "start_exchange", nullable = false)
    private Integer startExchange;
    
    @Column(name = "end_exchange")
    private Integer endExchange;  // NULL = active beat
    
    // === USER-PROVIDED CONTEXT ===
    
    /**
     * Natural language context provided by user when creating this beat.
     * Examples:
     * - "Let's see what the pack is doing outside"
     * - "Time skip to evening at Derek's loft"
     * - "Switch to Stiles' POV"
     * - "School cafeteria - Afternoon"
     * 
     * This is used to guide the narrator's opening narration for this beat.
     * No rigid structure - user can write anything that makes sense.
     */
    @Column(name = "transition_context", columnDefinition = "TEXT")
    private String transitionContext;
    
    // === GENERATED SUMMARY ===
    
    /**
     * Prose summary of entire beat, generated when beat ends.
     * Created from all events (major + minor) in this beat.
     * This becomes the authoritative record - after summary is created,
     * minor events are deleted from the database.
     * 
     * Size limit enforced in prompt (dynamic based on total beats),
     * not in schema.
     */
    @Column(columnDefinition = "TEXT")
    private String summary;
    
    // === TIMESTAMPS ===
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;  // Set when beat ends
    
    // === CONSTRUCTORS ===
    
    public Beat() {}
    
    public Beat(Stanza stanza, Integer beatNumber, Integer startExchange) {
        this.stanza = stanza;
        this.beatNumber = beatNumber;
        this.startExchange = startExchange;
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if this beat is currently active (not yet ended)
     */
    public boolean isActive() {
        return endExchange == null;
    }
    
    /**
     * End this beat with a summary
     */
    public void end(Integer finalExchange, String summary) {
        this.endExchange = finalExchange;
        this.summary = summary;
        this.completedAt = LocalDateTime.now();
    }
    
    /**
     * Get a human-readable label for this beat
     * Example: "Beat 2 (Exchanges 13-25)"
     */
    public String getLabel() {
        StringBuilder label = new StringBuilder();
        label.append("Beat ").append(beatNumber);
        label.append(" (Exchanges ").append(startExchange);
        if (endExchange != null) {
            label.append("-").append(endExchange);
        } else {
            label.append("+");
        }
        label.append(")");
        return label.toString();
    }
    
    /**
     * Get the transition context, or a default if null
     */
    public String getTransitionContextOrDefault() {
        if (transitionContext != null && !transitionContext.isEmpty()) {
            return transitionContext;
        }
        return beatNumber == 1 ? "Opening scene" : "Scene transition";
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
    
    public Integer getBeatNumber() {
        return beatNumber;
    }
    
    public void setBeatNumber(Integer beatNumber) {
        this.beatNumber = beatNumber;
    }
    
    public Integer getStartExchange() {
        return startExchange;
    }
    
    public void setStartExchange(Integer startExchange) {
        this.startExchange = startExchange;
    }
    
    public Integer getEndExchange() {
        return endExchange;
    }
    
    public void setEndExchange(Integer endExchange) {
        this.endExchange = endExchange;
    }
    
    public String getTransitionContext() {
        return transitionContext;
    }
    
    public void setTransitionContext(String transitionContext) {
        this.transitionContext = transitionContext;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
