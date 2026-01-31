package com.github.rrousso.erik_core.services.stanza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.persistence.entities.Beat;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.PromptLoaderService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating beat summaries.
 * 
 * When a beat ends, this service:
 * 1. Gathers all events from the beat (major + minor)
 * 2. Gets rolling synopsis for the beat (if exists)
 * 3. Calls LLM to create prose summary (2-3 paragraphs)
 * 4. Returns summary to be stored in Beat entity
 * 
 * Summary word count is dynamic based on total beats in stanza:
 * - More beats = shorter summaries per beat
 * - Fewer beats = longer summaries per beat
 * - Range: 100-500 words per beat
 */
@Service
public class BeatSummaryService {
    
    private static final Logger log = LoggerFactory.getLogger(BeatSummaryService.class);
    
    private final LLMClientService llmClient;
    private final PromptLoaderService promptLoader;
    
    private String summaryTemplate;
    
    // Configuration
    private static final int MAX_TOTAL_WORDS = 1500;
    private static final int MIN_WORDS_PER_BEAT = 100;
    private static final int MAX_WORDS_PER_BEAT = 500;
    
    public BeatSummaryService(LLMClientService llmClient, PromptLoaderService promptLoader) {
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
    }
    
    @jakarta.annotation.PostConstruct
    public void loadTemplate() {
        log.info("Loading beat summary prompt template");
        this.summaryTemplate = promptLoader.load("analytical/beat_summary.txt");
    }
    
    /**
     * Generate prose summary for a completed beat.
     * 
     * @param beat The beat being summarized
     * @param stanza The stanza containing the beat
     * @param conversationHistory For accessing recent exchanges (optional flavor)
     * @return Prose summary (2-3 paragraphs)
     */
    public String generateBeatSummary(Beat beat, Stanza stanza, ConversationHistory conversationHistory) {
        log.info("[BeatSummary] Generating summary for Beat {} (exchanges {}-{})", 
            beat.getBeatNumber(), beat.getStartExchange(), beat.getEndExchange());
        
        // 1. Get all events from this beat
        List<StanzaEvent> beatEvents = stanza.getEventsForBeat(beat);
        
        if (beatEvents.isEmpty()) {
            log.warn("[BeatSummary] No events found for beat {}, generating minimal summary", 
                beat.getBeatNumber());
            return generateMinimalSummary(beat);
        }
        
        // 2. Format events for prompt
        String eventsText = formatEventsForSummary(beatEvents);
        
        // 3. Get rolling synopsis for context (if available)
        String synopsis = conversationHistory != null ? conversationHistory.getSynopsis() : "";
        if (synopsis.isEmpty()) {
            synopsis = "[No synopsis available]";
        }
        
        // 4. Calculate dynamic word limit
        int totalBeats = stanza.getBeats().size();
        int maxWords = calculateMaxWords(totalBeats);
        
        // 5. Build prompt
        String prompt = buildPrompt(beat, eventsText, synopsis, maxWords, totalBeats);
        
        // 6. Call LLM
        try {
            String summary = llmClient.call(
                ModelType.ANALYTICAL,
                "You create concise beat summaries for interactive stories.",
                prompt
            );
            
            log.info("[BeatSummary] Generated summary ({} chars, target: ~{} words)", 
                summary.length(), maxWords);
            
            return summary;
            
        } catch (Exception e) {
            log.error("[BeatSummary] Failed to generate summary for beat {}", 
                beat.getBeatNumber(), e);
            return generateFallbackSummary(beat, beatEvents);
        }
    }
    
    /**
     * Calculate maximum words for this beat's summary based on total beats.
     * 
     * Formula: MAX_TOTAL_WORDS / totalBeats
     * Clamped between MIN_WORDS_PER_BEAT and MAX_WORDS_PER_BEAT
     */
    private int calculateMaxWords(int totalBeats) {
        if (totalBeats <= 0) {
            return MAX_WORDS_PER_BEAT;
        }
        
        int wordsPerBeat = MAX_TOTAL_WORDS / totalBeats;
        
        // Clamp to reasonable range
        return Math.max(MIN_WORDS_PER_BEAT, Math.min(MAX_WORDS_PER_BEAT, wordsPerBeat));
    }
    
    /**
     * Format events as text for the summary prompt.
     * Separates major and minor events for emphasis.
     */
    private String formatEventsForSummary(List<StanzaEvent> events) {
        StringBuilder sb = new StringBuilder();
        
        // Separate major and minor
        List<StanzaEvent> majorEvents = events.stream()
            .filter(StanzaEvent::isMajor)
            .collect(Collectors.toList());
        
        List<StanzaEvent> minorEvents = events.stream()
            .filter(e -> !e.isMajor())
            .collect(Collectors.toList());
        
        // Major events first
        if (!majorEvents.isEmpty()) {
            sb.append("MAJOR EVENTS (story-critical):\n");
            for (StanzaEvent event : majorEvents) {
                sb.append("- Exchange ").append(event.getExchangeNumber())
                  .append(": ").append(event.getDescription()).append("\n");
            }
            sb.append("\n");
        }
        
        // Minor events
        if (!minorEvents.isEmpty()) {
            sb.append("MINOR EVENTS (for flavor and context):\n");
            for (StanzaEvent event : minorEvents) {
                sb.append("- Exchange ").append(event.getExchangeNumber())
                  .append(": ").append(event.getDescription()).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Build the complete prompt for summary generation
     */
    private String buildPrompt(Beat beat, String eventsText, String synopsis, 
                               int maxWords, int totalBeats) {
        return summaryTemplate
            .replace("${beatNumber}", beat.getBeatNumber().toString())
            .replace("${transitionContext}", beat.getTransitionContextOrDefault())
            .replace("${startExchange}", beat.getStartExchange().toString())
            .replace("${endExchange}", beat.getEndExchange().toString())
            .replace("${eventsText}", eventsText)
            .replace("${synopsis}", synopsis)
            .replace("${maxWords}", String.valueOf(maxWords))
            .replace("${totalBeats}", String.valueOf(totalBeats));
    }
    
    /**
     * Generate minimal summary when no events exist
     */
    private String generateMinimalSummary(Beat beat) {
        String context = beat.getTransitionContextOrDefault();
        return String.format("Beat %d: %s (Exchanges %d-%d). Scene transition with minimal recorded events.", 
            beat.getBeatNumber(), context, beat.getStartExchange(), beat.getEndExchange());
    }
    
    /**
     * Generate fallback summary from events when LLM fails
     */
    private String generateFallbackSummary(Beat beat, List<StanzaEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("Beat ").append(beat.getBeatNumber()).append(": ");
        sb.append(beat.getTransitionContextOrDefault());
        sb.append(" (Exchanges ").append(beat.getStartExchange())
          .append("-").append(beat.getEndExchange()).append("). ");
        
        // List major events only
        List<StanzaEvent> majorEvents = events.stream()
            .filter(StanzaEvent::isMajor)
            .limit(5)
            .collect(Collectors.toList());
        
        if (!majorEvents.isEmpty()) {
            sb.append("Key events: ");
            sb.append(majorEvents.stream()
                .map(StanzaEvent::getDescription)
                .collect(Collectors.joining("; ")));
        }
        
        return sb.toString();
    }
}
