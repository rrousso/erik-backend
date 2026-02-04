package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    
    @ManyToOne(fetch = FetchType.LAZY)
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

    // === BLUEPRINT (3-TIER CHARACTER DEFINITION) ===
    // Tier 1: Archetype & Speech Pattern
    @Column(name = "blueprint_tier1_essentials", columnDefinition = "TEXT")
    private String blueprintTier1Essentials;

    // Tier 2: Primary Goal & Major Fear
    @Column(name = "blueprint_tier2_motivators", columnDefinition = "TEXT")
    private String blueprintTier2Motivators;

    // Tier 3: Visual anchors (array of 3 visual details)
    @Column(name = "blueprint_tier3_anchors", columnDefinition = "TEXT[]")
    private String[] blueprintTier3Anchors;

    // === KNOWLEDGE RELATIONSHIPS ===
    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterKnowledge> knownFacts = new ArrayList<>();
    
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
     * Check if character has any knowledge record for this fact.
     * 
     * Returns true if character has KNOWS or SUSPICIOUS state.
     * Returns false if character is UNAWARE (no record exists).
     */
    public boolean knowsFact(Fact fact) {
        return knownFacts.stream()
            .anyMatch(k -> k.getFact().getId().equals(fact.getId()));
    }
    
    /**
     * Check if character knows a fact (awareness state = KNOWS).
     * This is stricter than knowsFact - only returns true if they actually KNOW it.
     */
    public boolean actuallyKnowsFact(Fact fact) {
        return knownFacts.stream()
            .anyMatch(k -> k.getFact().getId().equals(fact.getId()) && k.knows());
    }
    
    /**
     * Check if character is unaware of a fact.
     */
    public boolean isUnawareOfFact(Fact fact) {
        return knownFacts.stream()
            .noneMatch(k -> k.getFact().getId().equals(fact.getId()));
    }
    
    /**
     * Check if character is suspicious about a fact.
     */
    public boolean isSuspiciousOfFact(Fact fact) {
        return knownFacts.stream()
            .anyMatch(k -> k.getFact().getId().equals(fact.getId()) && k.isSuspicious());
    }
    
    /**
     * Get the knowledge state for a specific fact (if tracked).
     */
    public CharacterKnowledge getKnowledgeStateFor(Fact fact) {
        return knownFacts.stream()
            .filter(k -> k.getFact().getId().equals(fact.getId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Format character for narrator prompt.
     * Shows character info + knowledge state using fact hashes.
     * 
     * @param restrictedFacts All restricted facts from stanza (to show DOES NOT KNOW)
     */
    public String toNarratorContext(List<Fact> restrictedFacts) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("**").append(name.toUpperCase()).append("**\n");
        
        if (canonRole != null && !canonRole.isEmpty()) {
            sb.append("Role: ").append(canonRole).append("\n");
        }
        
        if (relationshipToUser != null && !relationshipToUser.isEmpty()) {
            sb.append("Relationship to User: ").append(relationshipToUser).append("\n");
        }
        
        if (emotionalState != null && !emotionalState.isEmpty()) {
            sb.append("Emotional State: ").append(emotionalState).append("\n");
        }
        
        if (motivations != null && motivations.length > 0) {
            sb.append("Current Motivations:\n");
            for (String motivation : motivations) {
                sb.append("  - ").append(motivation).append("\n");
            }
        }
        
        if (knownFacts.isEmpty() && restrictedFacts.isEmpty()) {
            // No knowledge tracking needed
            return sb.toString();
        }
        
        // === CHARACTER KNOWLEDGE ===
        if (!knownFacts.isEmpty() || !restrictedFacts.isEmpty()) {
            sb.append("\n");
            
            // Collect fact hashes by awareness state
            List<String> knowsHashes = new ArrayList<>();
            List<String> suspectsHashes = new ArrayList<>();
            
            for (CharacterKnowledge ck : knownFacts) {
                String hash = com.github.rrousso.erik_core.util.FactUtility.extractHash(ck.getFact().getFactKey());
                if ("KNOWS".equals(ck.getAwarenessState())) {
                    knowsHashes.add(hash);
                } else if ("SUSPICIOUS".equals(ck.getAwarenessState())) {
                    suspectsHashes.add(hash);
                }
            }
            
            // Determine DOES NOT KNOW: restricted facts not in knownFacts
            Set<Long> trackedFactIds = knownFacts.stream()
                .map(ck -> ck.getFact().getId())
                .collect(Collectors.toSet());
                
            List<String> doesNotKnowHashes = new ArrayList<>();
            for (Fact restrictedFact : restrictedFacts) {
                if (!trackedFactIds.contains(restrictedFact.getId())) {
                    String hash = com.github.rrousso.erik_core.util.FactUtility.extractHash(restrictedFact.getFactKey());
                    doesNotKnowHashes.add(hash);
                }
            }
            
            // Output KNOWS
            if (!knowsHashes.isEmpty()) {
                sb.append("KNOWS (fact hashes):\n");
                for (String hash : knowsHashes) {
                    sb.append("  [").append(hash).append("]\n");
                }
            }
            
            // Output SUSPECTS
            if (!suspectsHashes.isEmpty()) {
                sb.append("SUSPECTS (fact hashes):\n");
                for (String hash : suspectsHashes) {
                    sb.append("  [").append(hash).append("]\n");
                }
            }
            
            // Output DOES NOT KNOW
            if (!doesNotKnowHashes.isEmpty()) {
                sb.append("DOES NOT KNOW (restricted fact hashes):\n");
                for (String hash : doesNotKnowHashes) {
                    sb.append("  [").append(hash).append("]\n");
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Format blueprint data for narrator context
     */
    public String formatBlueprintForNarrator() {
        if (blueprintTier1Essentials == null && blueprintTier2Motivators == null && blueprintTier3Anchors == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\nBLUEPRINT:\n");
        
        if (blueprintTier1Essentials != null && !blueprintTier1Essentials.isEmpty()) {
            sb.append("  Essentials: ").append(blueprintTier1Essentials).append("\n");
        }
        if (blueprintTier2Motivators != null && !blueprintTier2Motivators.isEmpty()) {
            sb.append("  Motivators: ").append(blueprintTier2Motivators).append("\n");
        }
        if (blueprintTier3Anchors != null && blueprintTier3Anchors.length > 0) {
            sb.append("  Visual Anchors: ").append(String.join(", ", blueprintTier3Anchors)).append("\n");
        }
        
        return sb.toString();
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
    public String getBlueprintTier1Essentials() {
        return blueprintTier1Essentials;
    }

    public void setBlueprintTier1Essentials(String blueprintTier1Essentials) {
        this.blueprintTier1Essentials = blueprintTier1Essentials;
    }

    public String getBlueprintTier2Motivators() {
        return blueprintTier2Motivators;
    }

    public void setBlueprintTier2Motivators(String blueprintTier2Motivators) {
        this.blueprintTier2Motivators = blueprintTier2Motivators;
    }

    public String[] getBlueprintTier3Anchors() {
        return blueprintTier3Anchors;
    }

    public void setBlueprintTier3Anchors(String[] blueprintTier3Anchors) {
        this.blueprintTier3Anchors = blueprintTier3Anchors;
    }
}
