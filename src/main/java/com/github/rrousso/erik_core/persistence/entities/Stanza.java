package com.github.rrousso.erik_core.persistence.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Main stanza container - the living state of a narrative simulation.
 * 
 * This is NOT a snapshot saved at the end - it's the active container
 * that gets updated every exchange throughout the stanza's lifetime.
 * 
 * BEAT INTEGRATION:
 * - Stanzas contain beats (scenes)
 * - Beat 1 is auto-created on stanza initialization
 * - User creates additional beats via ((next beat: context))
 */
@Entity
@Table(name = "stanzas")
public class Stanza {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;
    
    // === WORLD IDENTIFIER ===
    @Column(name = "world_identifier", length = 100)
    private String worldIdentifier;  // "teen_wolf", "marvel", "original", etc.
    
    // === STATUS ===
    @Column(length = 20)
    private String status = "active";  // active, paused, completed, abandoned
    
    // === WORLD CONTEXT (rarely changes) ===
    @Column(name = "time_context", length = 500)
    private String timeContext;
    
    @Column(name = "world_state", length = 1000)
    private String worldState;
    
    @Column(name = "world_rules", columnDefinition = "TEXT[]")
    private String[] worldRules;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String locations;  // JSONB array of {name, description, whoMightBeThere}
    
    // === SEARCH FIELDS (for /search command) ===
    @Column(length = 500)
    private String setting;
    
    @Column(length = 1000)
    private String premise;
    
    @Column(length = 200)
    private String tone;
    
    @Column(name = "quick_synopsis", length = 2000)
    private String quickSynopsis;
    
    // === TRACKING ===
    @Column(name = "current_beat")
    private Integer currentBeat = 1;  // Start at 1 (beat 1 auto-created)
    
    @Column(name = "current_exchange")
    private Integer currentExchange = 0;
    
    // === RELATIONSHIPS ===
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("beatNumber ASC")
    private List<Beat> beats = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaCharacter> characters = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Fact> facts = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tension> tensions = new ArrayList<>();
    
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaEvent> events = new ArrayList<>();
    
    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // === CONSTRUCTORS ===
    
    public Stanza() {}
    
    public Stanza(Persona persona, String worldIdentifier) {
        this.persona = persona;
        this.worldIdentifier = worldIdentifier;
    }
    
    // === STATUS CONVENIENCE METHODS ===
    
    public boolean isActive() {
        return "active".equals(status);
    }
    
    public boolean isPaused() {
        return "paused".equals(status);
    }
    
    public boolean isCompleted() {
        return "completed".equals(status);
    }
    
    public boolean isAbandoned() {
        return "abandoned".equals(status);
    }
    
    // === EXCHANGE TRACKING ===
    
    public void incrementExchange() {
        this.currentExchange++;
    }
    
    /**
     * @deprecated Use beat management methods instead
     */
    @Deprecated
    public void incrementBeat() {
        this.currentBeat++;
        this.currentExchange++;
    }
    
    // === BEAT MANAGEMENT ===
    
    /**
     * Initialize the first beat when stanza is created.
     * Should be called after stanza initialization.
     */
    public void initializeFirstBeat() {
        if (!beats.isEmpty()) {
            return; // Already initialized
        }
        
        Beat firstBeat = new Beat(this, 1, 1);
        firstBeat.setTransitionContext("Opening scene");
        beats.add(firstBeat);
    }
    
    /**
     * Get the currently active beat (endExchange = null)
     */
    public Beat getCurrentBeat() {
        return beats.stream()
            .filter(Beat::isActive)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get all completed beats (endExchange != null), sorted by beat number
     */
    public List<Beat> getCompletedBeats() {
        return beats.stream()
            .filter(b -> !b.isActive())
            .sorted(Comparator.comparing(Beat::getBeatNumber))
            .collect(Collectors.toList());
    }
    
    /**
     * Close the current beat by setting its end exchange.
     * This must be called BEFORE generating a summary, as the summary
     * needs to know the beat's temporal boundaries.
     * 
     * @return The beat that was just closed (for summary generation)
     */
    public Beat closeCurrentBeat() {
        Beat current = getCurrentBeat();
        if (current == null) {
            throw new IllegalStateException("No active beat to close");
        }
        
        current.setEndExchange(this.currentExchange);
        return current;
    }

    /**
     * Finalize a closed beat and start a new one.
     * The beat must already be closed (endExchange set).
     * 
     * @param closedBeat The beat to finalize
     * @param summary The generated summary for the beat
     * @param transitionContext Context for the new beat
     */
    public void finalizeBeatAndStartNew(Beat closedBeat, String summary, String transitionContext) {
        if (closedBeat.getEndExchange() == null) {
            throw new IllegalStateException("Beat must be closed before finalizing");
        }
        
        // Finalize the beat
        closedBeat.setSummary(summary);
        closedBeat.setCompletedAt(LocalDateTime.now());
        
        // Delete minor events from ended beat
        deleteMinorEventsFromBeat(closedBeat);
        
        // Calculate next beat number from actual beats (prevents duplicate key errors)
        int nextBeatNumber = beats.stream()
            .map(Beat::getBeatNumber)
            .max(Integer::compareTo)
            .orElse(0) + 1;
        
        // Update counter to match
        this.currentBeat = nextBeatNumber;
        
        // Create new beat
        Beat newBeat = new Beat(this, nextBeatNumber, this.currentExchange + 1);
        newBeat.setTransitionContext(transitionContext);
        beats.add(newBeat);
    }

    /**
     * @deprecated Use closeCurrentBeat() then finalizeBeatAndStartNew() instead
     */
    @Deprecated
    public void endCurrentBeatAndStartNew(String summary, String transitionContext) {
        Beat closed = closeCurrentBeat();
        finalizeBeatAndStartNew(closed, summary, transitionContext);
    }
    
    /**
     * End the current beat (for stanza completion).
     * Does NOT create a new beat.
     */
    public void endCurrentBeat(String summary) {
        Beat current = getCurrentBeat();
        if (current == null) {
            return; // No active beat
        }
        
        current.end(this.currentExchange, summary);
        deleteMinorEventsFromBeat(current);
    }
    
    /**
     * Delete all minor (non-major) events from a completed beat.
     * Major events are preserved for reference.
     */
    private void deleteMinorEventsFromBeat(Beat beat) {
        events.removeIf(event -> 
            event.getBeat() != null && 
            event.getBeat().equals(beat) && 
            !event.isMajor()
        );
    }
    
    /**
     * Get all events for a specific beat
     */
    public List<StanzaEvent> getEventsForBeat(Beat beat) {
        return events.stream()
            .filter(e -> e.getBeat() != null && e.getBeat().equals(beat))
            .sorted(Comparator.comparing(StanzaEvent::getExchangeNumber))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all events for the current beat
     */
    public List<StanzaEvent> getCurrentBeatEvents() {
        Beat current = getCurrentBeat();
        if (current == null) {
            return new ArrayList<>();
        }
        return getEventsForBeat(current);
    }
    
    // === CHARACTER CONVENIENCE METHODS ===
    
    public StanzaCharacter getUserCharacter() {
        return characters.stream()
            .filter(StanzaCharacter::isUser)
            .findFirst()
            .orElse(null);
    }
    
    public List<StanzaCharacter> getPresentCharacters() {
        return characters.stream()
            .filter(c -> "present".equals(c.getPresenceStatus()))
            .toList();
    }
    
    public List<StanzaCharacter> getPotentialCharacters() {
        return characters.stream()
            .filter(c -> "potential".equals(c.getPresenceStatus()))
            .toList();
    }
    
    // === TENSION CONVENIENCE METHODS ===
    
    public List<Tension> getActiveTensions() {
        return tensions.stream()
            .filter(t -> "active".equals(t.getStatus()))
            .toList();
    }
    
    public List<Tension> getHighPressureTensions() {
        return tensions.stream()
            .filter(t -> "active".equals(t.getStatus()))
            .filter(t -> t.getPressure() >= 7)
            .toList();
    }
    
    // === FORMAT FOR NARRATOR (with beat summaries) ===
    
    /**
     * Convert to a narrator-friendly context string.
     * 
     * NEW: Includes beat summaries for completed beats,
     * and current beat info (but not individual events - those are in synopsis)
     */
    /**
     * Convert to a narrator-friendly context string.
     * 
     * Includes beat summaries for completed beats,
     * current beat info, characters, tensions, and world context.
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== STANZA CONTEXT ===\n\n");
        
        // World identifier
        if (worldIdentifier != null && !worldIdentifier.isEmpty()) {
            sb.append("World: ").append(worldIdentifier.toUpperCase()).append("\n\n");
        }
        
        // 1. COMPLETED BEATS (Summary)
        String beatSummaries = formatCompletedBeatSummaries();
        if (!beatSummaries.isEmpty()) {
            sb.append("=== PREVIOUS BEATS (Summary) ===\n\n");
            sb.append(beatSummaries);
        }
        
        // 2. CURRENT BEAT (Active)
        Beat currentBeat = getCurrentBeat();
        if (currentBeat != null) {
            sb.append("=== CURRENT BEAT (Beat ").append(currentBeat.getBeatNumber()).append(") ===\n");
            
            String context = currentBeat.getTransitionContext();
            if (context != null && !context.isEmpty()) {
                sb.append("Scene Context: ").append(context).append("\n");
            }
            
            sb.append("Started: Exchange ").append(currentBeat.getStartExchange()).append("\n");
            sb.append("Current Exchange: ").append(this.currentExchange).append("\n\n");
        }
        
        // 3. FACT REGISTRY (must come before characters so narrator sees the full list)
        String factRegistry = formatFactRegistryForNarrator();
        if (!factRegistry.isEmpty()) {
            sb.append(factRegistry);
            sb.append("\n");
        }

        // Get restricted facts for character knowledge tracking
        List<Fact> restrictedFacts = facts.stream()
            .filter(Fact::isRestricted)
            .collect(Collectors.toList());

        // 4. USER CHARACTER
        StanzaCharacter userChar = getUserCharacter();
        if (userChar != null) {
            sb.append("=== USER CHARACTER ===\n\n");
            sb.append("**").append(userChar.getName()).append("**\n");
            if (userChar.getPublicRole() != null && !userChar.getPublicRole().isEmpty()) {
                sb.append("Public Role: ").append(userChar.getPublicRole()).append("\n");
            }
            if (userChar.getPrivateBackstory() != null && !userChar.getPrivateBackstory().isEmpty()) {
                sb.append("Private Backstory: ").append(userChar.getPrivateBackstory()).append("\n");
            }
            if (userChar.getCurrentLocation() != null && !userChar.getCurrentLocation().isEmpty()) {
                sb.append("Location: ").append(userChar.getCurrentLocation()).append("\n");
            }
            sb.append("\n");
        }
        
        // 5. PRESENT CHARACTERS
        List<StanzaCharacter> present = getPresentCharacters();
        if (!present.isEmpty()) {
            sb.append("=== CHARACTERS IN SCENE ===\n\n");
            for (StanzaCharacter c : present) {
                if (c.isUser()) continue; // Skip user, already shown above
                
                // toNarratorContext() handles all character info including blueprint and knowledge
                sb.append(c.toNarratorContext(restrictedFacts));
                sb.append(c.formatBlueprintForNarrator());
                sb.append("\n---\n\n");
            }
        }
        
        // 6. POTENTIAL CHARACTERS
        List<StanzaCharacter> potential = getPotentialCharacters();
        if (!potential.isEmpty()) {
            sb.append("=== CHARACTERS WHO MIGHT APPEAR ===\n");
            sb.append("(You MAY introduce these if narratively appropriate)\n\n");
            for (StanzaCharacter c : potential) {
                sb.append("- **").append(c.getName()).append("**");
                if (c.getCanonRole() != null && !c.getCanonRole().isEmpty()) {
                    sb.append(" (").append(c.getCanonRole()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // 7. ACTIVE TENSIONS
        List<Tension> activeTensions = getActiveTensions();
        if (!activeTensions.isEmpty()) {
            sb.append("=== ACTIVE NARRATIVE TENSIONS ===\n\n");
            for (Tension t : activeTensions) {
                sb.append("**").append(t.getDescription()).append("**\n");
                sb.append("Pressure: ").append(t.getPressure()).append("/10\n");
                if (t.getInvolvedCharacters() != null && !t.getInvolvedCharacters().isEmpty()) {
                    sb.append("Involved: ").append(t.getInvolvedCharacters()).append("\n");
                }
                sb.append("\n");
            }
        }
        
        // 8. WORLD CONTEXT
        if (timeContext != null && !timeContext.isEmpty()) {
            sb.append("=== WORLD CONTEXT ===\n\n");
            sb.append("**When:** ").append(timeContext).append("\n\n");
        }
        
        if (tone != null && !tone.isEmpty()) {
            sb.append("**Tone:**\n").append(tone).append("\n\n");
        }
        
        if (worldState != null && !worldState.isEmpty()) {
            sb.append("**Current World State:**\n").append(worldState).append("\n\n");
        }
        
        if (worldRules != null && worldRules.length > 0) {
            sb.append("**World Rules:**\n");
            for (String rule : worldRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Format fact registry for narrator prompt.
     * Shows all facts with their hashes so narrator can reference them.
     * Separates RESTRICTED (need special reveal modes) from PUBLIC (observable).
     */
    private String formatFactRegistryForNarrator() {
        StringBuilder sb = new StringBuilder();
        
        if (facts.isEmpty()) {
            return "";
        }
        
        sb.append("=== FACT REGISTRY ===\n\n");
        
        // Separate restricted from public
        List<Fact> restrictedFacts = facts.stream()
            .filter(Fact::isRestricted)
            .collect(Collectors.toList());
            
        List<Fact> publicFacts = facts.stream()
            .filter(f -> !f.isRestricted())
            .collect(Collectors.toList());
        
        // Show restricted facts (these are critical for preventing info bleed)
        if (!restrictedFacts.isEmpty()) {
            sb.append("**RESTRICTED FACTS** (characters can only learn through specific means):\n");
            for (Fact fact : restrictedFacts) {
                String hash = com.github.rrousso.erik_core.util.FactUtility.extractHash(fact.getFactKey());
                sb.append("  [").append(hash).append("] ")
                  .append(fact.getPredicate())
                  .append(" [reveal: ").append(fact.getAllowedRevealModes()).append("]\n");
            }
            sb.append("\n");
        }
        
        // Show public facts (observable)
        if (!publicFacts.isEmpty()) {
            sb.append("**PUBLIC FACTS** (observable by anyone present):\n");
            for (Fact fact : publicFacts) {
                String hash = com.github.rrousso.erik_core.util.FactUtility.extractHash(fact.getFactKey());
                sb.append("  [").append(hash).append("] ")
                  .append(fact.getPredicate()).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Format completed beat summaries as plain text.
     * Reusable for narrator prompts, extraction prompts, and synopsis generation.
     * 
     * @return Formatted beat summaries, or empty string if no completed beats
     */
    public String formatCompletedBeatSummaries() {
        List<Beat> completedBeats = getCompletedBeats();
        
        if (completedBeats.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Beat beat : completedBeats) {
            sb.append("Beat ").append(beat.getBeatNumber());
            
            String context = beat.getTransitionContext();
            if (context != null && !context.isEmpty()) {
                sb.append(" - ").append(context);
            }
            
            sb.append(" (Exchanges ").append(beat.getStartExchange())
              .append("-").append(beat.getEndExchange()).append(")\n\n");
            
            if (beat.getSummary() != null && !beat.getSummary().isEmpty()) {
                sb.append(beat.getSummary()).append("\n\n");
            } else {
                sb.append("[Summary not yet generated]\n\n");
            }
            
            sb.append("---\n\n");
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
    
    public Persona getPersona() {
        return persona;
    }
    
    public void setPersona(Persona persona) {
        this.persona = persona;
    }
    
    public String getWorldIdentifier() {
        return worldIdentifier;
    }
    
    public void setWorldIdentifier(String worldIdentifier) {
        this.worldIdentifier = worldIdentifier;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getTimeContext() {
        return timeContext;
    }
    
    public void setTimeContext(String timeContext) {
        this.timeContext = timeContext;
    }
    
    public String getWorldState() {
        return worldState;
    }
    
    public void setWorldState(String worldState) {
        this.worldState = worldState;
    }
    
    public String[] getWorldRules() {
        return worldRules;
    }
    
    public void setWorldRules(String[] worldRules) {
        this.worldRules = worldRules;
    }
    
    public String getLocations() {
        return locations;
    }
    
    public void setLocations(String locations) {
        this.locations = locations;
    }
    
    public String getSetting() {
        return setting;
    }
    
    public void setSetting(String setting) {
        this.setting = setting;
    }
    
    public String getPremise() {
        return premise;
    }
    
    public void setPremise(String premise) {
        this.premise = premise;
    }
    
    public String getTone() {
        return tone;
    }
    
    public void setTone(String tone) {
        this.tone = tone;
    }
    
    public String getQuickSynopsis() {
        return quickSynopsis;
    }
    
    public void setQuickSynopsis(String quickSynopsis) {
        this.quickSynopsis = quickSynopsis;
    }
    
    public Integer getCurrentBeatNumber() {
        // Note: This returns the beat NUMBER, not the Beat object
        // Use getCurrentBeat() method for the object
        return currentBeat;
    }
    
    public void setCurrentBeat(Integer currentBeat) {
        this.currentBeat = currentBeat;
    }
    
    public Integer getCurrentExchange() {
        return currentExchange;
    }
    
    public void setCurrentExchange(Integer currentExchange) {
        this.currentExchange = currentExchange;
    }
    
    public List<Beat> getBeats() {
        return beats;
    }
    
    public void setBeats(List<Beat> beats) {
        this.beats = beats;
    }
    
    public List<StanzaCharacter> getCharacters() {
        return characters;
    }
    
    public void setCharacters(List<StanzaCharacter> characters) {
        this.characters = characters;
    }
    
    public List<Fact> getFacts() {
        return facts;
    }
    
    public void setFacts(List<Fact> facts) {
        this.facts = facts;
    }
    
    public List<Tension> getTensions() {
        return tensions;
    }
    
    public void setTensions(List<Tension> tensions) {
        this.tensions = tensions;
    }
    
    public List<StanzaEvent> getEvents() {
        return events;
    }
    
    public void setEvents(List<StanzaEvent> events) {
        this.events = events;
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
