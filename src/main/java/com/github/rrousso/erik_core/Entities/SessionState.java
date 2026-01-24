package com.github.rrousso.erik_core.entities;

public class SessionState {

    public enum Mode {
        VOID,
        STANZA
    }

    private Mode mode = Mode.VOID;
    private ConversationHistory stanzaHistory;
    private ConversationHistory voidHistory;
    private StanzaMetadata currentStanza = null;
    private StanzaStatus stanzaStatus = StanzaStatus.NONE;
    private CompletedStanza completedStanza = null;
    
    // NEW: Loaded stanza from database for Erik to reference
    private StanzaRecord loadedStanzaMemory = null;

    public SessionState() {
        this.stanzaHistory = new ConversationHistory();
        this.voidHistory = new ConversationHistory();
    }

    // Getters
    public Mode getMode() {
        return mode;
    }

    public ConversationHistory getStanzaHistory() {
        return stanzaHistory;
    }

    public ConversationHistory getVoidHistory() {
        return voidHistory;
    }
    
    public StanzaMetadata getCurrentStanza() {
        return currentStanza;
    }

    public StanzaStatus getStanzaStatus() {
        return stanzaStatus;
    }

    public CompletedStanza getCompletedStanza() {
        return completedStanza;
    }
    
    public StanzaRecord getLoadedStanzaMemory() {
        return loadedStanzaMemory;
    }

    // Setters
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setCurrentStanza(StanzaMetadata currentStanza) {
        this.currentStanza = currentStanza;
    }

    public void setStanzaStatus(StanzaStatus stanzaStatus) {
        this.stanzaStatus = stanzaStatus;
    }

    public void setCompletedStanza(CompletedStanza completedStanza) {
        this.completedStanza = completedStanza;
    }
    
    public void setLoadedStanzaMemory(StanzaRecord loadedStanzaMemory) {
        this.loadedStanzaMemory = loadedStanzaMemory;
    }

    // Convenience methods for mode switching
    public void enterVoidMode() {
        this.mode = Mode.VOID;
    }

    public void enterStanzaMode() {
        this.mode = Mode.STANZA;
    }

    public boolean isInVoidMode() {
        return mode == Mode.VOID;
    }

    public boolean isInStanzaMode() {
        return mode == Mode.STANZA;
    }
    
    public boolean hasLoadedStanzaMemory() {
        return loadedStanzaMemory != null;
    }
}