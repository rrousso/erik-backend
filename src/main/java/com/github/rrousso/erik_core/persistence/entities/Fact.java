package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a fact - an atomic truth about the world.
 * 
 * Facts are append-only (no deletes, no edits). If something changes,
 * create a new fact that supersedes the old one.
 * 
 * Fact keys are limited to 50 characters and must be lowercase_snake_case.
 * Example: "user_is_spark", "derek_knows_about_nemeton"
 */
@Entity
@Table(name = "stanza_facts")
public class Fact {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    // === FACT IDENTITY ===
    
    /**
     * Unique key for this fact within the stanza.
     * Max 50 chars, lowercase_snake_case, one atomic piece of information.
     * Example: "user_is_spark", "stiles_was_possessed"
     */
    @Column(name = "fact_key", nullable = false, length = 50)
    private String factKey;
    
    /**
     * Kind of fact - helps categorize and filter.
     * USER_PRIVATE - secrets about user (often has a Secret lock)
     * USER_PUBLIC - observable user info
     * WORLD - general world truths
     * EVENT - something that happened
     * RELATIONSHIP - how characters relate
     * CHARACTER - fact about a non-user character
     */
    @Column(nullable = false, length = 30)
    private String kind;
    
    // === FACT CONTENT ===
    
    /**
     * Subject type: "user", "character", "location", "object", "world"
     */
    @Column(name = "subject_type", length = 30)
    private String subjectType;
    
    /**
     * Subject ID - references a character name, location name, etc.
     * For "user" type, this is null or the user's name.
     */
    @Column(name = "subject_id", length = 100)
    private String subjectId;
    
    /**
     * The predicate/property being stated.
     * Example: "is_spark", "was_possessed_by_nogitsune", "killed_user_family"
     */
    @Column(nullable = false, length = 100)
    private String predicate;
    
    /**
     * The value/object - can be simple (true/false) or complex (JSON).
     * Stored as JSONB for flexibility.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fact_value", columnDefinition = "jsonb")
    private String factValue;
    
    // === PROVENANCE ===
    
    /**
     * How this fact was established.
     * USER_SAID - user stated it during planning
     * OBSERVED - happened in scene
     * DOCUMENTED - from world canon/lore
     * NARRATOR_EMERGENT - narrator invented it
     * ARCHITECT_DERIVED - architect inferred it
     */
    @Column(length = 30)
    private String source;
    
    /**
     * Which beat this fact was created in.
     */
    @Column(name = "created_beat")
    private Integer createdBeat;
    
    /**
     * Which exchange this fact was created in.
     */
    @Column(name = "created_exchange")
    private Integer createdExchange;
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    // === CONSTRUCTORS ===
    
    public Fact() {}
    
    public Fact(Stanza stanza, String factKey, String kind, String predicate) {
        this.stanza = stanza;
        this.factKey = factKey;
        this.kind = kind;
        this.predicate = predicate;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isUserPrivate() {
        return "USER_PRIVATE".equals(kind);
    }
    
    public boolean isUserPublic() {
        return "USER_PUBLIC".equals(kind);
    }
    
    public boolean isEvent() {
        return "EVENT".equals(kind);
    }
    
    public boolean isAboutUser() {
        return "user".equals(subjectType);
    }
    
    public boolean isAboutCharacter(String characterName) {
        return "character".equals(subjectType) && characterName.equals(subjectId);
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
    
    public String getFactKey() {
        return factKey;
    }
    
    public void setFactKey(String factKey) {
        this.factKey = factKey;
    }
    
    public String getKind() {
        return kind;
    }
    
    public void setKind(String kind) {
        this.kind = kind;
    }
    
    public String getSubjectType() {
        return subjectType;
    }
    
    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }
    
    public String getSubjectId() {
        return subjectId;
    }
    
    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }
    
    public String getPredicate() {
        return predicate;
    }
    
    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }
    
    public String getFactValue() {
        return factValue;
    }
    
    public void setFactValue(String factValue) {
        this.factValue = factValue;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public Integer getCreatedBeat() {
        return createdBeat;
    }
    
    public void setCreatedBeat(Integer createdBeat) {
        this.createdBeat = createdBeat;
    }
    
    public Integer getCreatedExchange() {
        return createdExchange;
    }
    
    public void setCreatedExchange(Integer createdExchange) {
        this.createdExchange = createdExchange;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
