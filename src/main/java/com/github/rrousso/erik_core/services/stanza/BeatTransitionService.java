package com.github.rrousso.erik_core.services.stanza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.exceptions.llm.LLMException;
import com.github.rrousso.erik_core.exceptions.stanza.StanzaNotFoundException;
import com.github.rrousso.erik_core.persistence.entities.Beat;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling beat transitions in stanzas.
 * 
 * Beat transitions follow a 3-step process:
 * 1. Process any regular text (close current beat with final narration)
 * 2. Close beat, generate summary, start new beat
 * 3. Generate opening narration for new beat
 * 
 * This is an expensive operation (multiple LLM calls + extraction)
 * but ensures no information is lost during scene transitions.
 */
@Service
public class BeatTransitionService {
    
    private static final Logger log = LoggerFactory.getLogger(BeatTransitionService.class);
    
    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;
    private final BeatSummaryService beatSummaryService;
    private final StanzaExtractionService extractionService;
    
    // Regex patterns for parsing beat transitions
    private static final Pattern BEAT_WITH_TEXT = Pattern.compile(
        "^(.*?)\\(\\((?:next|new|start)\\s+beat:\\s*(.+?)\\)\\)\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern BEAT_ONLY = Pattern.compile(
        "^\\(\\((?:next|new|start)\\s+beat:\\s*(.+?)\\)\\)\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    
    public BeatTransitionService(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            BeatSummaryService beatSummaryService,
            StanzaExtractionService extractionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.beatSummaryService = beatSummaryService;
        this.extractionService = extractionService;
    }
    
    /**
     * Execute a beat transition.
     * 
     * @param userInput The user's input containing the beat directive
     * @param state The current session state
     * @return Response message for the user
     * @throws IllegalArgumentException if input format is invalid
     * @throws IllegalStateException if no active stanza or beat
     */
    @Transactional
    public String transitionToNextBeat(String userInput, SessionState state) {
        
    	Long stanzaId = state.getActiveStanzaId();
        
    	if(stanzaId == null) {
    		throw new StanzaNotFoundException("No stanza ID found in session state.");
    	}
    	
        // Load stanza ONCE
        Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        
    	if(stanza == null) {
    		throw new StanzaNotFoundException("No stanza found in session state.");
    	}
        
        // Parse
        BeatTransition transition = parseInput(userInput);
        
        StringBuilder output = new StringBuilder();
        
        // STEP 1: Process regular text 
        if (transition.hasRegularText()) {
            String closingNarration = processClosingNarration(
                stanza,  
                state, 
                transition.getRegularText()
            );
            output.append("\n[Narration] ").append(closingNarration).append("\n\n");
        }
        
        // STEP 2: Close and start 
        BeatTransitionResult result = closeAndStartBeat(stanza, state, transition.getTransitionContext());
        
        output.append("[System] Beat ")
              .append(result.getOldBeatNumber())
              .append(" ended. Beat ")
              .append(result.getNewBeatNumber())
              .append(" started.\n\n");
        
        // STEP 3: Generate opening narration
        String openingNarration = generateOpeningNarration(
            stanza,
            state,
            transition.getTransitionContext()
        );
        output.append("[Narration] ").append(openingNarration).append("\n");
        
        return output.toString();
    }
    
    /**
     * Process closing narration for the current beat.
     */
    private String processClosingNarration(@NonNull Stanza stanza, SessionState state, String regularText) {
        log.debug("[BeatTransition] Processing closing narration");
        
        String narration;
		try {
			narration = conversationService.converseWithNarrator(state, regularText);
		} catch (Exception e) {
			throw new LLMException("Failed to generate closing narration", e);
		}
        
        stanza.incrementExchange();
        ConversationHistory history = state.getStanzaHistory();
        boolean extracted = extractionService.forceExtraction(stanza, history);

        if (!extracted) {
            log.warn("[BeatTransition] Failed to extract state at beat boundary");
        }
        persistenceService.save(stanza);
        
        return narration;
    }
    
    /**
     * Close current beat, generate summary, and start new beat.
     */
    private BeatTransitionResult closeAndStartBeat(
            @NonNull Stanza stanza, 
            SessionState state, 
            String transitionContext) {
        
        log.debug("[BeatTransition] Closing current beat and starting new beat");

        // Close the current beat (sets endExchange)
        Beat closedBeat = stanza.closeCurrentBeat();
        if (closedBeat == null) {
            throw new IllegalStateException("No active beat found");
        }
        
        int oldBeatNum = closedBeat.getBeatNumber();
        
        log.debug("[BeatTransition] Beat {} closed at exchange {}", 
            oldBeatNum, closedBeat.getEndExchange());
        
        // Generate summary (beat now knows its boundaries)
        String summary = beatSummaryService.generateBeatSummary(
            closedBeat, 
            stanza,
            state.getStanzaHistory()
        );
        
        // Finalize beat and start new beat
        stanza.finalizeBeatAndStartNew(closedBeat, summary, transitionContext);
        
        // Clear rolling synopsis (fresh start for new beat)
        state.getStanzaHistory().clearHistory();
        
        // Save to database
        persistenceService.save(stanza);
        
        Beat newBeat = stanza.getCurrentBeat();
        int newBeatNum = newBeat != null ? newBeat.getBeatNumber() : oldBeatNum + 1;
        
        log.info("[BeatTransition] Beat transition complete: {} → {}", oldBeatNum, newBeatNum);
        
        return new BeatTransitionResult(oldBeatNum, newBeatNum);
    }
    
    /**
     * Generate opening narration for new beat.
     */
    private String generateOpeningNarration(@NonNull Stanza stanza, SessionState state, String transitionContext) {
        log.debug("[BeatTransition] Generating opening narration");
        
        // Call narrator with transition context
        String narration;
		try {
			narration = conversationService.converseWithNarrator(
			    state,
			    "((The scene transitions: " + transitionContext + "))"
			);
		} catch (Exception e) {
			throw new LLMException("Failed to generate opening narration", e);
		}
        
		ConversationHistory history = state.getStanzaHistory();
		boolean extracted = extractionService.forceExtraction(stanza, history);

		if (!extracted) {
		    log.warn("[BeatTransition] Failed to extract state at beat boundary");
		}
        persistenceService.save(stanza);
        
        return narration;
    }
    
    /**
     * Parse beat transition from user input.
     */
    private BeatTransition parseInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        
        // Try format with regular text
        Matcher withText = BEAT_WITH_TEXT.matcher(input.trim());
        if (withText.matches()) {
            String regularText = withText.group(1).trim();
            String transitionContext = withText.group(2).trim();
            
            if (transitionContext.isEmpty()) {
                return null;
            }
            
            return new BeatTransition(regularText, transitionContext);
        }
        
        // Try format with just beat marker
        Matcher beatOnly = BEAT_ONLY.matcher(input.trim());
        if (beatOnly.matches()) {
            String transitionContext = beatOnly.group(1).trim();
            
            if (transitionContext.isEmpty()) {
                return null;
            }
            
            return new BeatTransition("", transitionContext);
        }
        
        return null;
    }
    
    // ========== INNER CLASSES ==========
    
    /**
     * Parsed beat transition input
     */
    private static class BeatTransition {
        private final String regularText;
        private final String transitionContext;
        
        public BeatTransition(String regularText, String transitionContext) {
            this.regularText = regularText != null ? regularText : "";
            this.transitionContext = transitionContext;
        }
        
        public boolean hasRegularText() {
            return !regularText.isEmpty();
        }
        
        public String getRegularText() {
            return regularText;
        }
        
        public String getTransitionContext() {
            return transitionContext;
        }
    }
    
    /**
     * Result of beat transition
     */
    private static class BeatTransitionResult {
        private final int oldBeatNumber;
        private final int newBeatNumber;
        
        public BeatTransitionResult(int oldBeatNumber, int newBeatNumber) {
            this.oldBeatNumber = oldBeatNumber;
            this.newBeatNumber = newBeatNumber;
        }
        
        public int getOldBeatNumber() {
            return oldBeatNumber;
        }
        
        public int getNewBeatNumber() {
            return newBeatNumber;
        }
    }
}