package com.github.rrousso.erik_core.services;

import com.github.rrousso.erik_core.Entities.ConversationHistory;
import com.github.rrousso.erik_core.Entities.ModelType;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Spring service for generating rolling synopses with dual-model support
 */
@Service
public class SynopsisGeneratorService {
    
	private static final Logger log = LoggerFactory.getLogger(SynopsisGeneratorService.class);
	
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final ConfigService configService;
    
    public SynopsisGeneratorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder, ConfigService configService) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.configService = configService;
    }
    
    /**
     * Generate rolling synopsis for current conversation segment
     * Uses NARRATIVE model (Claude) for quality
     */
    public String generateSynopsis(ConversationHistory history) throws Exception {
    	
        int threshold = getSynopsisThreshold();
    	

        if (history.getCurrentHistorySize() < threshold) {
            return history.getSynopsis();
        }

        String exchangeText = generateExchange(history);
        

        if (exchangeText.isEmpty()) {
            log.info("[Synopsis] No old messages to condense yet. Skipping synopsis generation.");
            return history.getSynopsis();
        }
        
        log.info("[Synopsis] Exchange text to be condensed (" + exchangeText.length() + " chars):");

        String previousSynopsis = history.getSynopsis();
        log.info("[Synopsis] Previous synopsis (" + previousSynopsis.length() + " chars):");

        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous synopsis]";
        }

        log.info("[System] Generating synopsis...");

        String synopsisPrompt = "Condense the following conversation into a brief synopsis. " +
            "Extract key events, decisions, and important details. " +
            "Previous synopsis: " + previousSynopsis + "\n\n" +
            "Recent exchanges:\n" + exchangeText + "\n\n" +
            "Create an updated synopsis:";


        // Use NARRATIVE model for rolling synopsis (needs quality)
        String newSynopsis = llmClient.call(
            ModelType.ANALYTICAL,
            "You are a helpful assistant that creates concise summaries.",
            synopsisPrompt
        );

        log.info("[Synopsis] Generated new synopsis (" + newSynopsis.length() + " chars):");

        history.updateSynopsis(newSynopsis, getWindowSize());

        return newSynopsis;
    }
    
    /**
     * Extract what changes user wants during pause
     * Uses ANALYTICAL model (Gemini) for simple extraction
     */
    public String generatePauseChanges(ConversationHistory history) throws Exception {
        
        List<ConversationHistory.Message> voidMessages = history.getMessagesForAPI();
        
        // Build conversation text
        StringBuilder conversationText = new StringBuilder();
        for (ConversationHistory.Message msg : voidMessages) {
            conversationText.append(msg.getRole().toUpperCase())
                           .append(": ")
                           .append(msg.getContent())
                           .append("\n\n");
        }
        
        String systemPrompt = promptBuilder.buildChangeDistillerPrompt();
        String userPrompt = conversationText.toString();
        
        // Use ANALYTICAL model for change detection (simple extraction)
        return llmClient.call(
            ModelType.ANALYTICAL,
            systemPrompt,
            userPrompt
        );
    }

    /**
     * Generate exchanges text for synopsis
     * FIXED: Returns empty string if no old messages to condense
     */
    private String generateExchange(ConversationHistory history) {
        List<ConversationHistory.Message> exchanges = 
        		history.getExchangesForSynopsis(history, getWindowSize());
        
        if (exchanges.isEmpty()) {
            return "";  // Return empty string, not synopsis
        }
        
        StringBuilder exchangeText = new StringBuilder();
        for (ConversationHistory.Message msg : exchanges) {
            exchangeText.append(msg.getRole().toUpperCase())
                       .append(": ")
                       .append(msg.getContent())
                       .append("\n\n");
        }
        return exchangeText.toString();
    }
    
    /**
     * Generate quick synopsis for stanza identification
     * Uses ANALYTICAL model (Gemini) - simple summarization
     */
    public String generateQuickSynopsis(ConversationHistory history) throws Exception {

        List<ConversationHistory.Message> messages = history.getMessagesForAPI();

        String systemPrompt = promptBuilder.buildQuickSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the quick narrative summary.";

        log.info("[QuickSynopsis] System prompt length: " + systemPrompt.length() + " chars");

        // Use ANALYTICAL model (Gemini) - fast and cheap for brief summaries
        String result = llmClient.callWithHistory(
            ModelType.ANALYTICAL,
            systemPrompt,
            userPrompt,
            messages
        );

        return result;
    }

    /**
     * Generate detailed synopsis for future reference
     * Uses NARRATIVE model (Claude) - needs comprehensive quality
     */
    public String generateDetailedSynopsis(ConversationHistory history) throws Exception {


        List<ConversationHistory.Message> messages = history.getMessagesForAPI();

        String systemPrompt = promptBuilder.buildDetailedSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the detailed setup document.";

        log.info("[DetailedSynopsis] System prompt length: " + systemPrompt.length() + " chars");

        // Use NARRATIVE model (Claude) - comprehensive documentation needs quality
        String result = llmClient.callWithHistory(
            ModelType.NARRATIVE,
            systemPrompt,
            userPrompt,
            messages
        );

        return result;
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
}