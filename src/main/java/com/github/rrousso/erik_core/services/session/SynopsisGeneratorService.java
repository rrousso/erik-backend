package com.github.rrousso.erik_core.services.session;

import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.services.config.PersonaService;
import com.github.rrousso.erik_core.services.config.SynopsisConfigService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.SystemPromptBuilderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring service for generating synopses using template-based prompts.
 * 
 * NEW BEHAVIOR (Synopsis FROM Events):
 * - Synopsis is generated from EXTRACTED EVENTS in the database
 * - Events are the source of truth, synopsis is the narrative view
 * - Recent exchanges still included for narrative flavor
 * - No more redundancy: events → synopsis (not exchanges → synopsis → events)
 * 
 * BEAT INTEGRATION:
 * - Synopsis organizes events by beat
 * - Current beat gets detailed event listing
 * - Completed beats already have summaries (don't need synopsis)
 */
@Service
public class SynopsisGeneratorService {
    
    private static final Logger log = LoggerFactory.getLogger(SynopsisGeneratorService.class);
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final SynopsisConfigService synopsisConfig;
    private final PersonaService personaService;
    
    // File to save synopsis for debugging
    private static final String QUICK_SYNOPSIS_DEBUG_FILE = "user_data/quick_synopsis.txt";
    private static final String ROLLING_SYNOPSIS_DEBUG_FILE = "user_data/rolling_synopsis.txt";
    private static final String DISTILLED_CHANGES_DEBUG_FILE = "user_data/distilled_changes.txt";
    
    public SynopsisGeneratorService(
            LLMClientService llmClient, 
            SystemPromptBuilderService promptBuilder, 
            PersonaService personaService,
            SynopsisConfigService synopsisConfig) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.personaService = personaService;
        this.synopsisConfig = synopsisConfig;
    }
     
    /** 
     * Generate rolling synopsis using world_snapshot_synopsis template.
     * 
     * NEW: Uses extracted events from database as source of truth.
     * Recent exchanges are included for narrative flavor only.
     * 
     * @param history The conversation history
     * @param stanza The stanza entity (for accessing events)
     */
    public String generateSynopsis(ConversationHistory history, Stanza stanza) throws Exception {
        
        int threshold = getSynopsisThreshold();
        
        if (history.getCurrentHistorySize() < threshold) {
            log.debug("[Synopsis] History size {} below threshold {}, skipping", 
                history.getCurrentHistorySize(), threshold);
            return history.getSynopsis();
        }
        
        // Calculate which exchanges to condense
        int windowSize = getWindowSize();
        int historySize = history.getCurrentHistorySize();
        int keepCount = windowSize;
        int oldMessagesCount = historySize - keepCount;
        
        if (oldMessagesCount <= 0) {
            log.info("[Synopsis] No old messages to condense yet. Skipping synopsis generation.");
            return history.getSynopsis();
        }
        
        // NEW: Get events for old exchanges (from database, not conversation history)
        int startExchange = 1;  // Or track where last synopsis ended
        int endExchange = oldMessagesCount;
        
        List<StanzaEvent> eventsToCondense = stanza.getEvents().stream()
            .filter(e -> e.getExchangeNumber() >= startExchange && 
                         e.getExchangeNumber() <= endExchange)
            .sorted((e1, e2) -> Integer.compare(e1.getExchangeNumber(), e2.getExchangeNumber()))
            .collect(Collectors.toList());
        
        log.info("[Synopsis] Condensing {} events from exchanges {}-{}", 
            eventsToCondense.size(), startExchange, endExchange);
        
        // Format events as text for prompt
        String eventsText = formatEventsForSynopsis(eventsToCondense);
        log.debug("[Synopsis] Events text ({} chars)", eventsText.length());
        
        // Get recent raw exchanges for flavor (this returns a String)
        String recentExchangesText = history.getRecentExchangesForSystemPrompt();
        log.debug("[Synopsis] Recent exchanges text ({} chars)", recentExchangesText.length());
        
        // Get previous synopsis
        String previousSynopsis = history.getSynopsis();
        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous snapshot]";
        }
        log.info("[Synopsis] Previous synopsis ({} chars)", previousSynopsis.length());
        
        // Get template and fill it in
        String template = promptBuilder.buildRollingSynopsisPrompt(personaService.getUserPersona());
        String filledPrompt = template
            .replace("${previousSnapshot}", previousSynopsis)
            .replace("${extractedEvents}", eventsText)
            .replace("${recentExchanges}", recentExchangesText);
        
        log.info("[System] Generating rolling synopsis using rolling_synopsis template (events-based)...");
        
        // Use ANALYTICAL model
        String newSynopsis = llmClient.call(
            ModelType.ANALYTICAL,
            "You create concise world snapshot synopses from extracted events.",
            filledPrompt
        );
        
        log.info("[Synopsis] Generated new synopsis ({} chars)", newSynopsis.length());
        
        history.updateSynopsis(newSynopsis, windowSize);
        
        // Save synopsis to file for inspection
        saveSynopsisToFile(newSynopsis, "rolling", ROLLING_SYNOPSIS_DEBUG_FILE);
        
        return newSynopsis;
    }
    
    /**
     * Format events for synopsis prompt.
     * Groups by beat for better organization.
     */
    private String formatEventsForSynopsis(List<StanzaEvent> events) {
        if (events.isEmpty()) {
            return "[No events recorded]";
        }
        
        // Group by beat number
        Map<Integer, List<StanzaEvent>> eventsByBeat = events.stream()
            .collect(Collectors.groupingBy(StanzaEvent::getBeatNumber));
        
        StringBuilder sb = new StringBuilder();
        
        for (Integer beatNum : eventsByBeat.keySet().stream().sorted().collect(Collectors.toList())) {
            List<StanzaEvent> beatEvents = eventsByBeat.get(beatNum);
            
            sb.append("Beat ").append(beatNum).append(":\n");
            
            // Major events first (emphasized)
            List<StanzaEvent> major = beatEvents.stream()
                .filter(StanzaEvent::isMajor)
                .collect(Collectors.toList());
            
            List<StanzaEvent> minor = beatEvents.stream()
                .filter(e -> !e.isMajor())
                .collect(Collectors.toList());
            
            if (!major.isEmpty()) {
                for (StanzaEvent e : major) {
                    sb.append("  - Exchange ").append(e.getExchangeNumber())
                      .append(": ").append(e.getDescription())
                      .append(" (MAJOR)\n");
                }
            }
            
            if (!minor.isEmpty()) {
                for (StanzaEvent e : minor) {
                    sb.append("  - Exchange ").append(e.getExchangeNumber())
                      .append(": ").append(e.getDescription())
                      .append("\n");
                }
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate quick synopsis using template.
     * Uses beat summaries + rolling synopsis + current messages for complete context.
     * 
     * @param history Conversation history (for rolling synopsis + recent messages)
     * @param stanza Stanza entity (for beat summaries)
     */
    public String generateQuickSynopsis(ConversationHistory history, Stanza stanza) throws Exception {
        
        // 1. Get completed beat summaries (reuses Stanza's existing formatter)
        String beatSummaries = stanza.formatCompletedBeatSummaries();
        if (beatSummaries.isEmpty()) {
            beatSummaries = "[No completed beats - this is the opening beat]";
        }
        
        // 2. Get rolling synopsis (current beat's compressed exchanges)
        String rollingSynopsis = history.getSynopsis();
        
        // 3. Get recent raw messages
        String recentMessages = formatMessagesAsText(history.getAllMessages(), true);
        
        log.info("[QuickSynopsis] Using {} completed beats + rolling synopsis ({} chars) + {} recent messages", 
            stanza.getCompletedBeats().size(),
            rollingSynopsis.length(),
            history.getAllMessages().size());
        
        // Get template and fill it in
        String template = promptBuilder.buildQuickSynopsisPrompt(personaService.getUserPersona());
        String filledPrompt = template
            .replace("${beatSummaries}", beatSummaries)
            .replace("${rollingSynopsis}", rollingSynopsis.isEmpty() ? "[No synopsis]" : rollingSynopsis)
            .replace("${conversationText}", recentMessages);
        
        log.info("[QuickSynopsis] Generating quick synopsis...");
        
        // Use ANALYTICAL model
        String result = llmClient.call(
            ModelType.ANALYTICAL,
            filledPrompt,
            "Create the brief narrative summary."
        );
        
        log.info("[QuickSynopsis] Generated (" + result.length() + " chars)");
        
        // Save quick synopsis to file
        saveSynopsisToFile(result, "quick", QUICK_SYNOPSIS_DEBUG_FILE);
        
        return result;
    }
    
    /**
     * Extract what changes user wants during pause.
     */
    public String generatePauseChanges(ConversationHistory history) throws Exception {
        
        String conversationText = formatMessagesAsText(history.getAllMessages(), false);
        
        String systemPrompt = promptBuilder.buildChangeDistillerPrompt();
        
        // Use ANALYTICAL model for change detection
        String result = llmClient.call(
            ModelType.ANALYTICAL,
            systemPrompt,
            conversationText
        );
        
        log.info("[Distilled Changes] Generated (" + result.length() + " chars)");
        
        // Save to file
        saveSynopsisToFile(result, "distilled", DISTILLED_CHANGES_DEBUG_FILE);
        
        return result;
    }
    
    /**
     * Helper: Format message list as text.
     * @param stripOOC If true, removes text in ((double parentheses))
     */
    private String formatMessagesAsText(List<ConversationHistory.Message> messages, boolean stripOOC) {
        if (messages.isEmpty()) {
            return "";
        }
        
        StringBuilder text = new StringBuilder();
        for (ConversationHistory.Message msg : messages) {
            String content = msg.getContent();
            
            // Strip OOC commands if requested
            if (stripOOC) {
                content = stripOOCCommands(content);
            }
            
            // Skip if content is empty after stripping
            if (content.trim().isEmpty()) {
                continue;
            }
            
            text.append(msg.getRole().toUpperCase())
                .append(": ")
                .append(content)
                .append("\n\n");
        }
        return text.toString();
    }
    
    /**
     * Remove out-of-character commands in ((double parentheses))
     */
    private String stripOOCCommands(String text) {
        // Remove text in ((double parentheses))
        String stripped = text.replaceAll("\\(\\([^)]*\\)\\)", "");
        
        // Clean up extra whitespace
        stripped = stripped.replaceAll("\\s+", " ").trim();
        
        return stripped;
    }
    
    public boolean shouldGenerateSynopsis(ConversationHistory history) {
        int threshold = getSynopsisThreshold();
        return history.getCurrentHistorySize() >= threshold;
    }
    
    private int getWindowSize() {
        return synopsisConfig.getWindowSize();
    }
    
    private int getSynopsisThreshold() {
        return synopsisConfig.getThresholdSize();
    }
    
    /**
     * Save synopsis to file for debugging.
     */
    private void saveSynopsisToFile(String synopsis, String type, String path) {
        try {
            Path filePath = Paths.get(path);
            
            // Ensure parent directory exists
            Files.createDirectories(filePath.getParent());
            
            // Create formatted output with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder output = new StringBuilder();
            output.append("=".repeat(80)).append("\n");
            output.append("SYNOPSIS UPDATE - ").append(type.toUpperCase()).append("\n");
            output.append("Timestamp: ").append(timestamp).append("\n");
            output.append("=".repeat(80)).append("\n\n");
            output.append(synopsis);
            output.append("\n\n");
            
            // Write to file (overwrite mode - always shows latest)
            Files.writeString(filePath, output.toString(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
            
            log.info("[Synopsis] Saved to file: {}", filePath.toAbsolutePath());
            
        } catch (IOException e) {
            log.warn("[Synopsis] Failed to save to file: {}", e.getMessage());
            // Don't throw - this is just a debugging feature
        }
    }
}