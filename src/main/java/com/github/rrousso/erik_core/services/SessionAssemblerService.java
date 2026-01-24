package com.github.rrousso.erik_core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.entities.ConversationHistory;
import com.github.rrousso.erik_core.entities.SessionContext;
import com.github.rrousso.erik_core.entities.SessionState;

/**
 * Assembles SessionContext snapshots from SessionState and other sources.
 * 
 * This service answers: "Who is Erik right now, and what does he know?"
 * 
 * It does NOT:
 * - Control flow
 * - Render prompts
 * - Talk to the LLM
 * 
 * It ONLY assembles truth into a structured snapshot.
 */
@Service
public class SessionAssemblerService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionAssemblerService.class);
    
    private final ConfigService configService;
    
    public SessionAssemblerService(ConfigService configService) {
        this.configService = configService;
    }
    
    /**
     * Assemble context for VOID mode (talking to Erik)
     * 
     * Uses void history for synopsis and recent exchanges.
     * May include stanza setup if paused, and completed stanza for reflection.
     * May include loaded stanza memory from /load command.
     */
    public SessionContext assembleForVoid(SessionState state) {
        log.debug("Assembling context for VOID mode, status: {}", state.getStanzaStatus());
        
        ConversationHistory voidHistory = state.getVoidHistory();
        ConversationHistory stanzaHistory = state.getStanzaHistory();
        
        SessionContext context = SessionContext.builder()
            .userPersona(configService.getUserPersona())
            .mode(SessionState.Mode.VOID)
            .stanzaStatus(state.getStanzaStatus())
            .stanzaMetadata(state.getCurrentStanza())           // May be null, or paused stanza
            .synopsis(stanzaHistory.getSynopsis())
            .recentExchanges(voidHistory.getRecentExchangesForSystemPrompt())
            .completedStanza(state.getCompletedStanza())     // For reflection after end/abandon
            .loadedStanzaMemory(state.getLoadedStanzaMemory()) // From /load command
            .build();
        
        log.debug("Assembled VOID context - hasSynopsis: {}, hasRecentExchanges: {}, hasStanzaSetup: {}, hasCompletedStanza: {}, hasLoadedMemory: {}",
            context.hasSynopsis(),
            context.hasRecentExchanges(),
            context.hasStanzaSetup(),
            context.hasCompletedStanza(),
            context.hasLoadedStanzaMemory());
         
        return context;
    }
    
    /**
     * Assemble context for STANZA mode (narrator)
     * 
     * Uses stanza history for synopsis and recent exchanges.
     * Requires a stanza setup to be present.
     * Note: Loaded stanza memory is not passed to narrator (it's for Erik only)
     */
    public SessionContext assembleForStanza(SessionState state) {
        log.debug("Assembling context for STANZA mode");
        
        if (state.getCurrentStanza() == null) {
            throw new IllegalStateException("Cannot assemble stanza context without a StanzaMetadata");
        }
        
        ConversationHistory stanzaHistory = state.getStanzaHistory();
        
        SessionContext context = SessionContext.builder()
            .userPersona(configService.getUserPersona())
            .mode(SessionState.Mode.STANZA)
            .stanzaStatus(state.getStanzaStatus())
            .stanzaMetadata(state.getCurrentStanza())
            .synopsis(stanzaHistory.getSynopsis())
            .recentExchanges(stanzaHistory.getRecentExchangesForSystemPrompt())
            // Note: loadedStanzaMemory not passed to narrator - it's for Erik/planning only
            .build();
        
        log.debug("Assembled STANZA context - hasSynopsis: {}, hasRecentExchanges: {}, setup premise: {}",
            context.hasSynopsis(),
            context.hasRecentExchanges(),
            context.getStanzaSetup().getPremise());
        
        return context;
    }
}