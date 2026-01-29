package com.github.rrousso.erik_core.domain.models;

import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.persistence.entities.Stanza;

/**
 * Immutable snapshot of everything needed for ONE LLM call.
 * This is a dumb data container - no logic, no decisions.
 * 
 * Created by SessionAssemblerService, consumed by SystemPromptBuilderService.
 * 
 * UPDATE: Now supports narratorContextFromDB as alternative to InitializedStanza
 * for stanza mode. This allows loading context from the database.
 */
public class SessionContext {
    
    // === IDENTITY ===
    private final String userPersona;
    
    // === CURRENT SITUATION ===
    private final SessionState.Mode mode;
    private final InitializedStanza initializedStanza;        // null if in VOID mode or using DB context
    private final StanzaStatus stanzaStatus;
    
    // === NARRATOR CONTEXT FROM DB ===
    // Alternative to InitializedStanza - pre-formatted context string loaded from database
    private final String narratorContextFromDB;
    
    // === MEMORY ===
    private final String synopsis;
    private final String recentExchanges;
    
    // === COMPLETED STANZA (for reflection after end/abandon) ===
    private final CompletedStanza completedStanza; // null if none
    
    // === LOADED STANZA MEMORY (from /load command) ===
    private final Stanza loadedStanzaMemory; // null if none
    
    // Private constructor - use builder
    private SessionContext(Builder builder) {
        this.userPersona = builder.userPersona;
        this.mode = builder.mode;
        this.initializedStanza = builder.initializedStanza;
        this.stanzaStatus = builder.stanzaStatus;
        this.narratorContextFromDB = builder.narratorContextFromDB;
        this.synopsis = builder.synopsis != null ? builder.synopsis : "";
        this.recentExchanges = builder.recentExchanges != null ? builder.recentExchanges : "";
        this.completedStanza = builder.completedStanza;
        this.loadedStanzaMemory = builder.loadedStanzaMemory;
    }
    
    // === GETTERS (no setters - immutable) ===
    
    public String getUserPersona() {
        return userPersona;
    }
    
    public SessionState.Mode getMode() {
        return mode;
    }
    
    public StanzaStatus getStanzaStatus() {
        return stanzaStatus;
    }
    
    public String getSynopsis() {
        return synopsis;
    }
    
    public String getRecentExchanges() {
        return recentExchanges;
    }
    
    public CompletedStanza getCompletedStanza() {
        return completedStanza;
    }
    
    public Stanza getLoadedStanzaMemory() {
        return loadedStanzaMemory;
    }
    
    public InitializedStanza getInitializedStanza() {
        return initializedStanza;
    }
    
    public String getNarratorContextFromDB() {
        return narratorContextFromDB;
    }
    
    // === CONVENIENCE METHODS ===
    
    public boolean hasInitializedStanza() {
        return initializedStanza != null;
    }
    
    /**
     * Check if we have narrator context from DB (alternative to InitializedStanza)
     */
    public boolean hasNarratorContext() {
        return narratorContextFromDB != null && !narratorContextFromDB.isEmpty();
    }
    
    /**
     * Get narrator context - prefers DB context, falls back to InitializedStanza
     * This is the method SystemPromptBuilderService should use
     */
    public String getNarratorContext() {
        if (hasNarratorContext()) {
            return narratorContextFromDB;
        } else if (hasInitializedStanza()) {
            return initializedStanza.toNarratorContext();
        }
        return null;
    }
    
    public boolean hasSynopsis() {
        return synopsis != null && !synopsis.isEmpty();
    }
    
    public boolean hasRecentExchanges() {
        return recentExchanges != null && !recentExchanges.isEmpty();
    }
    
    public boolean hasCompletedStanza() {
        return completedStanza != null;
    }
    
    public boolean hasLoadedStanzaMemory() {
        return loadedStanzaMemory != null;
    }
    
    // === BUILDER ===
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String userPersona;
        private SessionState.Mode mode;
        private InitializedStanza initializedStanza;
        private StanzaStatus stanzaStatus;
        private String narratorContextFromDB;
        private String synopsis;
        private String recentExchanges;
        private CompletedStanza completedStanza;
        private Stanza loadedStanzaMemory;
        
        public Builder userPersona(String userPersona) {
            this.userPersona = userPersona;
            return this;
        }
        
        public Builder initializedStanza(InitializedStanza initializedStanza) {
            this.initializedStanza = initializedStanza;
            return this;
        }

        public Builder mode(SessionState.Mode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder stanzaStatus(StanzaStatus stanzaStatus) {
            this.stanzaStatus = stanzaStatus;
            return this;
        }
        
        public Builder narratorContextFromDB(String narratorContextFromDB) {
            this.narratorContextFromDB = narratorContextFromDB;
            return this;
        }
        
        public Builder synopsis(String synopsis) {
            this.synopsis = synopsis;
            return this;
        }
        
        public Builder recentExchanges(String recentExchanges) {
            this.recentExchanges = recentExchanges;
            return this;
        }
        
        public Builder completedStanza(CompletedStanza completedStanza) {
            this.completedStanza = completedStanza;
            return this;
        }
        
        public Builder loadedStanzaMemory(Stanza loadedStanzaMemory) {
            this.loadedStanzaMemory = loadedStanzaMemory;
            return this;
        }
        
        public SessionContext build() {
            return new SessionContext(this);
        }
    }
}