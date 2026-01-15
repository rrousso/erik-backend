package com.github.rrousso.erik_core.entities;

public class SessionState {

    public enum Mode {
        VOID,
        STANZA
    }

    private Mode mode = Mode.VOID;
    private ConversationHistory stanzaHistory;
    private ConversationHistory voidHistory;
    private StanzaSetup currentStanza = null;
    private StanzaStatus stanzaStatus = StanzaStatus.NONE;
    private CompletedStanza completedStanza = null;

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
    
    public StanzaSetup getCurrentStanza() {
        return currentStanza;
    }

    public StanzaStatus getStanzaStatus() {
        return stanzaStatus;
    }

    public CompletedStanza getCompletedStanza() {
        return completedStanza;
    }

    // Setters
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setCurrentStanza(StanzaSetup currentStanza) {
        this.currentStanza = currentStanza;
    }

    public void setStanzaStatus(StanzaStatus stanzaStatus) {
        this.stanzaStatus = stanzaStatus;
    }

    public void setCompletedStanza(CompletedStanza completedStanza) {
        this.completedStanza = completedStanza;
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
}