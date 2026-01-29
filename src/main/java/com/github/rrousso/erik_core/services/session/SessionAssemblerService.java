package com.github.rrousso.erik_core.services.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.domain.models.SessionContext;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.config.ConfigService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

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
 * 
 * UPDATE: Now loads stanza data from database when activeStanzaId is present,
 * falling back to InitializedStanza for backward compatibility.
 */
@Service
public class SessionAssemblerService {
    
    private static final Logger log = LoggerFactory.getLogger(SessionAssemblerService.class);
    
    private final ConfigService configService;
    private final StanzaPersistenceService persistenceService;
    
    public SessionAssemblerService(ConfigService configService, StanzaPersistenceService persistenceService) {
        this.configService = configService;
        this.persistenceService = persistenceService;
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
            .initializedStanza(state.getInitializedStanza())           // May be null, or paused stanza
            .synopsis(stanzaHistory.getSynopsis())
            .recentExchanges(voidHistory.getRecentExchangesForSystemPrompt())
            .completedStanza(state.getCompletedStanza())     // For reflection after end/abandon
            .loadedStanzaMemory(state.getLoadedStanzaMemory()) // From /load command
            .build();
        
        log.debug("Assembled VOID context - hasSynopsis: {}, hasRecentExchanges: {}, hasStanzaSetup: {}, hasCompletedStanza: {}, hasLoadedMemory: {}",
            context.hasSynopsis(),
            context.hasRecentExchanges(),
            context.hasInitializedStanza(),
            context.hasCompletedStanza(),
            context.hasLoadedStanzaMemory());
         
        return context;
    }
    
    /**
     * Assemble context for STANZA mode (narrator)
     * 
     * Uses stanza history for synopsis and recent exchanges.
     * 
     * NEW BEHAVIOR: If activeStanzaId is present, loads context from database.
     * Falls back to InitializedStanza if no DB record (backward compatibility).
     * 
     * Note: Loaded stanza memory is not passed to narrator (it's for Erik only)
     */
    public SessionContext assembleForStanza(SessionState state) {
        log.debug("Assembling context for STANZA mode");
        
        ConversationHistory stanzaHistory = state.getStanzaHistory();
        
        // Try to load narrator context from database first
        String narratorContext = null;
        Long stanzaId = state.getActiveStanzaId();
        
        if (state.hasActiveStanza() && stanzaId != null) {
            narratorContext = loadNarratorContextFromDB(stanzaId);
        }
        
        // Build context - use DB context if available, otherwise fall back to InitializedStanza
        SessionContext.Builder builder = SessionContext.builder()
            .userPersona(configService.getUserPersona())
            .mode(SessionState.Mode.STANZA)
            .stanzaStatus(state.getStanzaStatus())
            .synopsis(stanzaHistory.getSynopsis())
            .recentExchanges(stanzaHistory.getRecentExchangesForSystemPrompt());
        
        if (narratorContext != null) {
            // We have DB context - use it
            builder.narratorContextFromDB(narratorContext);
            log.debug("Assembled STANZA context from DATABASE");
        } else if (state.getInitializedStanza() != null) {
            // Fall back to InitializedStanza
            builder.initializedStanza(state.getInitializedStanza());
            log.debug("Assembled STANZA context from InitializedStanza (fallback)");
        } else {
            throw new IllegalStateException("Cannot assemble stanza context: no DB record or InitializedStanza");
        }
        
        SessionContext context = builder.build();
        
        log.debug("Assembled STANZA context - hasSynopsis: {}, hasRecentExchanges: {}, hasNarratorContext: {}",
            context.hasSynopsis(),
            context.hasRecentExchanges(),
            context.hasNarratorContext());
        
        return context;
    }
    
    /**
     * Load narrator context string from the database.
     * Returns null if loading fails (allows fallback to InitializedStanza).
     */
    private String loadNarratorContextFromDB(@NonNull Long stanzaId) {
        try {
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
            String context = stanza.toNarratorContext();
            log.debug("Loaded narrator context from DB for stanza ID: {}", stanzaId);
            return context;
        } catch (Exception e) {
            log.warn("Failed to load narrator context from DB for stanza ID: {} - will use fallback", stanzaId, e);
            return null;
        }
    }
}