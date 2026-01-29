package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a character in the stanza, including the user.
 * 
 * User character has isUser=true and does NOT have secret restrictions
 * (user controls their own kayfabe).
 * 
 * Non-user characters have knowledge tracked via CharacterKnowledge
 * and secret access tracked via CharacterSecretState.
 */
@Entity
@Table(name = "stanza_characters")
public class StanzaCharacter {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;
    
    // === IDENTITY ===
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "is_user")
    private boolean isUser = false;
    
    @Column(name = "canon_role", length = 300)
    private String canonRole;  // Only for non-original characters, can note canon divergences
    
    // === PRESENCE ===
    @Column(name = "presence_status", length = 20)
    private String presenceStatus = "background";  // present, potential, background
    
    @Column(name = "current_location", length = 200)
    private String currentLocation;
    
    // === USER-ONLY FIELDS (only populated when isUser=true) ===
    @Column(name = "public_role", length = 500)
    private String publicRole;  // What others can observe
    
    @Column(name = "private_backstory", length = 2000)
    private String privateBackstory;  // Narrator-only, characters don't know
    
    @Column(name = "visible_traits", columnDefinition = "TEXT[]")
    private String[] visibleTraits;
    
    // === CHARACTER STATE ===
    @Column(name = "emotional_state", length = 300)
    private String emotionalState;
    
    @Column(columnDefinition = "TEXT[]")
    private String[] motivations;
    
    @Column(name = "relationship_to_user", length = 300)
    private String relationshipToUser;
    
    @Column(columnDefinition = "TEXT[]")
    private String[] goals;
    
    // === KNOWLEDGE RELATIONSHIPS ===
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterKnowledge> knownFacts = new ArrayList<>();
    
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterSecretState> secretStates = new ArrayList<>();
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public StanzaCharacter() {}
    
    public StanzaCharacter(Stanza stanza, String name) {
        this.stanza = stanza;
        this.name = name;
    }
    
    public static StanzaCharacter createUserCharacter(Stanza stanza, String name) {
        StanzaCharacter user = new StanzaCharacter(stanza, name);
        user.setUser(true);
        user.setPresenceStatus("present");  // User is always present
        return user;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean isPresent() {
        return "present".equals(presenceStatus);
    }
    
    public boolean isPotential() {
        return "potential".equals(presenceStatus);
    }
    
    public boolean isBackground() {
        return "background".equals(presenceStatus);
    }
    
    /**
     * Check if character knows a specific fact
     */
    public boolean knowsFact(Fact fact) {
        return knownFacts.stream()
            .anyMatch(k -> k.getFact().getId().equals(fact.getId()));
    }
    
    /**
     * Check if character has access to a secret (can learn the locked fact)
     */
    public boolean hasSecretAccess(Secret secret) {
        return secretStates.stream()
            .anyMatch(s -> s.getSecret().getId().equals(secret.getId()) 
                       && "knows".equals(s.getState()));
    }
    
    /**
     * Get secret state for a specific secret
     */
    public String getSecretState(Secret secret) {
        return secretStates.stream()
            .filter(s -> s.getSecret().getId().equals(secret.getId()))
            .map(CharacterSecretState::getState)
            .findFirst()
            .orElse("unaware");
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isUser() {
        return isUser;
    }
    
    public void setUser(boolean isUser) {
        this.isUser = isUser;
    }
    
    public String getCanonRole() {
        return canonRole;
    }
    
    public void setCanonRole(String canonRole) {
        this.canonRole = canonRole;
    }
    
    public String getPresenceStatus() {
        return presenceStatus;
    }
    
    public void setPresenceStatus(String presenceStatus) {
        this.presenceStatus = presenceStatus;
    }
    
    public String getCurrentLocation() {
        return currentLocation;
    }
    
    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }
    
    public String getPublicRole() {
        return publicRole;
    }
    
    public void setPublicRole(String publicRole) {
        this.publicRole = publicRole;
    }
    
    public String getPrivateBackstory() {
        return privateBackstory;
    }
    
    public void setPrivateBackstory(String privateBackstory) {
        this.privateBackstory = privateBackstory;
    }
    
    public String[] getVisibleTraits() {
        return visibleTraits;
    }
    
    public void setVisibleTraits(String[] visibleTraits) {
        this.visibleTraits = visibleTraits;
    }
    
    public String getEmotionalState() {
        return emotionalState;
    }
    
    public void setEmotionalState(String emotionalState) {
        this.emotionalState = emotionalState;
    }
    
    public String[] getMotivations() {
        return motivations;
    }
    
    public void setMotivations(String[] motivations) {
        this.motivations = motivations;
    }
    
    public String getRelationshipToUser() {
        return relationshipToUser;
    }
    
    public void setRelationshipToUser(String relationshipToUser) {
        this.relationshipToUser = relationshipToUser;
    }
    
    public String[] getGoals() {
        return goals;
    }
    
    public void setGoals(String[] goals) {
        this.goals = goals;
    }
    
    public List<CharacterKnowledge> getKnownFacts() {
        return knownFacts;
    }
    
    public void setKnownFacts(List<CharacterKnowledge> knownFacts) {
        this.knownFacts = knownFacts;
    }
    
    public List<CharacterSecretState> getSecretStates() {
        return secretStates;
    }
    
    public void setSecretStates(List<CharacterSecretState> secretStates) {
        this.secretStates = secretStates;
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
