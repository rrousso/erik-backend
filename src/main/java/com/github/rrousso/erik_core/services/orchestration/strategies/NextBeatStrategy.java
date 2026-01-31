package com.github.rrousso.erik_core.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.persistence.entities.Beat;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.orchestration.ConversationService;
import com.github.rrousso.erik_core.services.stanza.BeatSummaryService;
import com.github.rrousso.erik_core.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for handling NEXT_BEAT flag.
 * 
 * Implements the 3-step beat transition flow:
 * 1. Process regular text (close current beat with final narration)
 * 2. End current beat, generate summary, start new beat
 * 3. Generate opening narration for new beat
 * 
 * User input format:
 * - "[regular text] ((next beat: transition context))"
 * - "((next beat: transition context))" (no regular text)
 * 
 * This is an expensive operation (multiple LLM calls + extraction)
 * but ensures no information is lost during scene transitions.
 */
@Component
public class NextBeatStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(NextBeatStrategy.class);
    
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
    
    public NextBeatStrategy(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            BeatSummaryService beatSummaryService,
            StanzaExtractionService extractionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.beatSummaryService = beatSummaryService;
        this.extractionService = extractionService;
    }
    
    @Override
    public String execute(String userInput, SessionState state) {
        // Validate: Must be in stanza mode
        if (!state.isInStanzaMode()) {
            log.warn("Attempt to create new beat while in void mode");
            return "[System] Beat transitions only work during active stanzas.\n";
        }
        
        // Validate: Must have active stanza ID
        Long stanzaId = state.getActiveStanzaId();
        if (stanzaId == null) {
            log.error("[NextBeat] No active stanza ID in state");
            return "[System] No active stanza found.\n";
        }
        
        // Parse input
        BeatTransition transition = parseInput(userInput);
        if (transition == null) {
            log.warn("Failed to parse beat transition from input: {}", userInput);
            return "[System] Invalid beat format. Use: ((next beat: context))\n";
        }
        
        log.info("[NextBeat] Starting beat transition. Has regular text: {}, Context: '{}'", 
            transition.hasRegularText(), transition.getTransitionContext());
        
        StringBuilder output = new StringBuilder();
        
        // STEP 1: Process regular text (if any) - Close current beat
        if (transition.hasRegularText()) {
            try {
                log.debug("[NextBeat] Step 1: Processing regular text (closing current beat)");
                String closingNarration = conversationService.converseWithNarrator(
                    state, 
                    transition.getRegularText()
                );
                output.append("\n[Narration] ").append(closingNarration).append("\n\n");
                
                // Extract state changes from closing narration
                Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
                if (stanza == null) {
                    log.error("[NextBeat] Failed to load stanza {}", stanzaId);
                    return "[System] Error loading stanza data.\n";
                }
                
                extractionService.extractAndUpdate(
                    stanza,
                    transition.getRegularText(),
                    closingNarration
                );
                persistenceService.save(stanza);
                
            } catch (Exception e) {
                log.error("[NextBeat] Failed to process closing narration", e);
                return "[System] Error processing narration. Please try again.\n";
            }
        }
        
        // STEP 2: End current beat, start new beat
        int oldBeatNum = 0;
        int newBeatNum = 0;
        
        try {
            log.debug("[NextBeat] Step 2: Ending current beat and starting new beat");
            
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
            if (stanza == null) {
                log.error("[NextBeat] Failed to load stanza {}", stanzaId);
                return "[System] Error loading stanza data.\n";
            }
            
            Beat currentBeat = stanza.getCurrentBeat();
            if (currentBeat == null) {
                log.error("[NextBeat] No active beat found in stanza");
                return "[System] No active beat found. This shouldn't happen!\n";
            }
            
            oldBeatNum = currentBeat.getBeatNumber();
            
            // Generate summary for current beat
            log.debug("[NextBeat] Generating summary for beat {}", oldBeatNum);
            String summary = beatSummaryService.generateBeatSummary(
                currentBeat, 
                stanza,
                state.getStanzaHistory()
            );
            
            // End current beat and start new beat
            stanza.endCurrentBeatAndStartNew(summary, transition.getTransitionContext());
            
            // Clear rolling synopsis (fresh start for new beat)
            state.getStanzaHistory().clearHistory();
            
            // Save to database
            persistenceService.save(stanza);
            
            Beat newBeat = stanza.getCurrentBeat();
            if (newBeat != null) {
                newBeatNum = newBeat.getBeatNumber();
            } else {
                log.warn("[NextBeat] New beat is null after creation");
                newBeatNum = oldBeatNum + 1;  // Best guess
            }
            
            log.info("[NextBeat] Beat transition complete: {} → {}", oldBeatNum, newBeatNum);
            
            output.append("[System] Beat ")
                  .append(oldBeatNum)
                  .append(" ended. Beat ")
                  .append(newBeatNum)
                  .append(" started.\n\n");
            
        } catch (Exception e) {
            log.error("[NextBeat] Failed to transition beats", e);
            return "[System] Error transitioning beats. Please try again.\n";
        }
        
        // STEP 3: Generate opening narration for new beat
        try {
            log.debug("[NextBeat] Step 3: Generating opening narration for beat {}", newBeatNum);
            
            String openingNarration = generateBeatOpeningNarration(
                state,
                transition.getTransitionContext()
            );
            
            output.append("[Narration] ").append(openingNarration).append("\n");
            
            // Extract state changes from opening narration
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
            if (stanza != null) {
                extractionService.extractAndUpdate(
                    stanza,
                    "((transition))",  // Special marker for beat transition
                    openingNarration
                );
                persistenceService.save(stanza);
            } else {
                log.warn("[NextBeat] Could not load stanza for opening extraction");
            }
            
        } catch (Exception e) {
            log.error("[NextBeat] Failed to generate opening narration", e);
            output.append("[System] Beat created but opening narration failed. Continue normally.\n");
        }
        
        return output.toString();
    }
    
    /**
     * Generate opening narration for new beat.
     * This is a special narrator call with transition context guidance.
     */
    private String generateBeatOpeningNarration(SessionState state, String transitionContext) 
            throws Exception {
        
        // Build special prompt for beat transition
        // The narrator receives:
        // - Previous beat summaries
        // - Transition context from user
        // - Special instruction to establish new scene
        
        // For now, use a simplified approach - just call narrator with special instruction
        // TODO: Create SessionAssemblerService.assembleForBeatTransition() 
        // and SystemPromptBuilderService.buildBeatTransitionPrompt()
        
        return conversationService.converseWithNarrator(
            state,
            "((The scene transitions: " + transitionContext + "))"
        );
    }
    
    /**
     * Parse beat transition from user input.
     * Supports formats:
     * - "regular text ((next beat: context))"
     * - "((next beat: context))"
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
                return null;  // Context is required
            }
            
            return new BeatTransition(regularText, transitionContext);
        }
        
        // Try format with just beat marker
        Matcher beatOnly = BEAT_ONLY.matcher(input.trim());
        if (beatOnly.matches()) {
            String transitionContext = beatOnly.group(1).trim();
            
            if (transitionContext.isEmpty()) {
                return null;  // Context is required
            }
            
            return new BeatTransition("", transitionContext);
        }
        
        return null;
    }
    
    /**
     * Internal class to hold parsed beat transition info
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
}