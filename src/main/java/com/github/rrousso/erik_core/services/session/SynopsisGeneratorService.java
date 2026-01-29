package com.github.rrousso.erik_core.services.session;

import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.services.config.ConfigService;
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

/**
 * Spring service for generating synopses using template-based prompts.
 * Strips OOC commands and uses synopsis + currentHistory for complete context.
 * ENHANCED: Now saves synopsis to file for inspection
 */
@Service
public class SynopsisGeneratorService {
    
	private static final Logger log = LoggerFactory.getLogger(SynopsisGeneratorService.class);
	
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final ConfigService configService;
    
    // File to save synopsis for debugging
    private static final String QUICK_SYNOPSIS_DEBUG_FILE = "user_data/quick_synopsis.txt";
    private static final String ROLLING_SYNOPSIS_DEBUG_FILE = "user_data/rolling_synopsis.txt";
    private static final String DISTILLED_CHANGES_DEBUG_FILE = "user_data/distilled_changes.txt";
    
    public SynopsisGeneratorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder, ConfigService configService) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.configService = configService;
    }
    
    /** 
     * Generate rolling synopsis using world_snapshot_synopsis template
     * Uses ANALYTICAL model (Gemini) for efficiency
     * Uses trimmed currentHistory (frequent, efficient updates)
     */
    public String generateSynopsis(ConversationHistory history) throws Exception {
    	
        int threshold = getSynopsisThreshold();
    	
        if (history.getCurrentHistorySize() < threshold) {
            return history.getSynopsis();
        }

        // Get OLD messages that need to be condensed
        List<ConversationHistory.Message> oldMessages = history.getExchangesForSynopsis(getWindowSize());
        
        if (oldMessages.isEmpty()) {
            log.info("[Synopsis] No old messages to condense yet. Skipping synopsis generation.");
            return history.getSynopsis();
        }
        
        // Format old messages as text (with OOC stripped)
        String exchangeText = formatMessagesAsText(oldMessages, true);
        log.info("[Synopsis] Exchange text to be condensed (" + exchangeText.length() + " chars)");

        String previousSynopsis = history.getSynopsis();
        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous snapshot]";
        }
        log.info("[Synopsis] Previous synopsis (" + previousSynopsis.length() + " chars)");

        // Get template and fill it in
        String template = promptBuilder.buildWorldSnapshotPrompt(configService.getUserPersona());
        String filledPrompt = template
            .replace("${previousSnapshot}", previousSynopsis)
            .replace("${exchangeText}", exchangeText);

        log.info("[System] Generating rolling synopsis using world_snapshot template...");

        // Use ANALYTICAL model - simple call with filled template as user prompt
        String newSynopsis = llmClient.call(
            ModelType.ANALYTICAL,
            "You create concise world snapshot synopses.",
            filledPrompt
        );

        log.info("[Synopsis] Generated new synopsis (" + newSynopsis.length() + " chars)");

        history.updateSynopsis(newSynopsis, getWindowSize());
        
        // ENHANCEMENT: Save synopsis to file for inspection
        saveSynopsisToFile(newSynopsis, "rolling", ROLLING_SYNOPSIS_DEBUG_FILE);

        return newSynopsis;
    }
    
    /**
     * Generate quick synopsis using template
     * Uses ANALYTICAL model (Gemini) for speed
     * Uses synopsis + currentHistory for complete context
     */
    public String generateQuickSynopsis(ConversationHistory history) throws Exception {

        String synopsis = history.getSynopsis();
        String recentMessages = formatMessagesAsText(history.getAllMessages(), true);
        
        log.info("[QuickSynopsis] Using synopsis (" + synopsis.length() + " chars) + " +
                "recent messages (" + history.getAllMessages().size() + " messages)");

        // Get template and fill it in
        String template = promptBuilder.buildQuickSynopsisPrompt(configService.getUserPersona());
        String filledPrompt = template
            .replace("${previousSnapshot}", synopsis.isEmpty() ? "[Start of stanza]" : synopsis)
            .replace("${conversationText}", recentMessages);

        log.info("[QuickSynopsis] Generating quick synopsis...");

        // Use ANALYTICAL model
        String result = llmClient.call(
            ModelType.ANALYTICAL,
            filledPrompt,
            "Create the brief narrative summary."
        );

        log.info("[QuickSynopsis] Generated (" + result.length() + " chars)");
        
        // ENHANCEMENT: Save quick synopsis to file
        saveSynopsisToFile(result, "quick",QUICK_SYNOPSIS_DEBUG_FILE);

        return result;
    }
    
    /**
     * Extract what changes user wants during pause
     * Uses ANALYTICAL model (Gemini) for simple extraction
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
        
        // ENHANCEMENT: Save quick synopsis to file
        saveSynopsisToFile(result, "distilled",DISTILLED_CHANGES_DEBUG_FILE);

        return result;
    }

    /**
     * Helper: Format message list as text
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
     * This prevents meta-awareness in synopses
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
        return configService.getWindowSize();
    }
    
    private int getSynopsisThreshold() {
        return configService.getThresholdSize();
    }
    
    /**
     * ENHANCEMENT: Save synopsis to file for debugging
     * This allows you to check the synopsis even if console output is truncated
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