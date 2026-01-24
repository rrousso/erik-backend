package com.github.rrousso.erik_core.entities;

/**
 * Immutable snapshot of everything needed for ONE LLM call.
 * This is a dumb data container - no logic, no decisions.
 * 
 * Created by SessionAssemblerService, consumed by SystemPromptBuilderService.
 */
public class SessionContext {
    
    // === IDENTITY ===
    private final String userPersona;
    
    // === CURRENT SITUATION ===
    private final SessionState.Mode mode;
    private final StanzaMetadata stanzaMetadata;        // null if in VOID mode without paused stanza
    private final StanzaStatus stanzaStatus;
    
    // === MEMORY ===
    private final String synopsis;
    private final String recentExchanges;
    
    // === COMPLETED STANZA (for reflection after end/abandon) ===
    private final CompletedStanza completedStanza; // null if none
    
    // === LOADED STANZA MEMORY (from /load command) ===
    private final StanzaRecord loadedStanzaMemory; // null if none
    
    // Private constructor - use builder
    private SessionContext(Builder builder) {
        this.userPersona = builder.userPersona;
        this.mode = builder.mode;
        this.stanzaMetadata = builder.stanzaMetadata;
        this.stanzaStatus = builder.stanzaStatus;
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
    
    public StanzaMetadata getStanzaSetup() {
        return stanzaMetadata;
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
    
    public StanzaRecord getLoadedStanzaMemory() {
        return loadedStanzaMemory;
    }
    
    // === CONVENIENCE METHODS (still dumb, just null/empty checks) ===
    
    public boolean hasStanzaSetup() {
        return stanzaMetadata != null;
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
        private StanzaMetadata stanzaMetadata;
        private StanzaStatus stanzaStatus;
        private String synopsis;
        private String recentExchanges;
        private CompletedStanza completedStanza;
        private StanzaRecord loadedStanzaMemory;
        
        public Builder userPersona(String userPersona) {
            this.userPersona = userPersona;
            return this;
        }
        
        public Builder mode(SessionState.Mode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder stanzaMetadata(StanzaMetadata stanzaMetadata) {
            this.stanzaMetadata = stanzaMetadata;
            return this;
        }
        
        public Builder stanzaStatus(StanzaStatus stanzaStatus) {
            this.stanzaStatus = stanzaStatus;
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
        
        public Builder loadedStanzaMemory(StanzaRecord loadedStanzaMemory) {
            this.loadedStanzaMemory = loadedStanzaMemory;
            return this;
        }
        
        public SessionContext build() {
            return new SessionContext(this);
        }
    }
}