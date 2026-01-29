package com.github.rrousso.erik_core.domain.models;

import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;

public class SessionState {

    public enum Mode {
        VOID,
        STANZA
    }

    private Mode mode = Mode.VOID;
    private ConversationHistory stanzaHistory;
    private ConversationHistory voidHistory;
    private StanzaStatus stanzaStatus = StanzaStatus.NONE;
    private CompletedStanza completedStanza = null;
    private InitializedStanza initializedStanza = null;
    
    // Database ID of the active stanza (replaces storing full InitializedStanza long-term)
    private Long activeStanzaId = null;
    
    // Loaded stanza from database for Erik to reference
    private Stanza loadedStanzaMemory = null;

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
    
    public StanzaStatus getStanzaStatus() {
        return stanzaStatus;
    }

    public CompletedStanza getCompletedStanza() {
        return completedStanza;
    }
    
    public InitializedStanza getInitializedStanza() {
        return initializedStanza;
    }
    
    public Long getActiveStanzaId() {
        return activeStanzaId;
    }
    
    public Stanza getLoadedStanzaMemory() {
        return loadedStanzaMemory;
    }

    // Setters
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setInitializedStanza(InitializedStanza initializedStanza) {
        this.initializedStanza = initializedStanza;
    }
    
    public void setActiveStanzaId(Long activeStanzaId) {
        this.activeStanzaId = activeStanzaId;
    }

    public void setStanzaStatus(StanzaStatus stanzaStatus) {
        this.stanzaStatus = stanzaStatus;
    }

    public void setCompletedStanza(CompletedStanza completedStanza) {
        this.completedStanza = completedStanza;
    }
    
    public void setLoadedStanzaMemory(Stanza loadedStanzaMemory) {
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
    
    public boolean hasActiveStanza() {
        return activeStanzaId != null;
    }
}