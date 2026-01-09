package com.github.rrousso.erik_core.conversation;

import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.llm.ModelType;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring service for generating rolling synopses with dual-model support
 */
@Service
public class SynopsisGeneratorService {
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    
    public SynopsisGeneratorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }
    
    /**
     * Generate rolling synopsis for current conversation segment
     * Uses NARRATIVE model (Claude) for quality
     */
    public String generateSynopsis(ConversationHistory history) throws Exception {
        
        if (!history.shouldGenerateSynopsis()) {
            return history.getSynopsis();
        }
        
        String exchangeText = generateExchange(history);
        
        String previousSynopsis = history.getSynopsis();
        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous synopsis]";
        }
        
        System.out.println("[System] Generating synopsis...");
        
        String synopsisPrompt = "Condense the following conversation into a brief synopsis. " +
            "Extract key events, decisions, and important details. " +
            "Previous synopsis: " + previousSynopsis + "\n\n" +
            "Recent exchanges:\n" + exchangeText + "\n\n" +
            "Create an updated synopsis:";

        // Use NARRATIVE model for rolling synopsis (needs quality)
        String newSynopsis = llmClient.call(
            ModelType.NARRATIVE,
            "You are a helpful assistant that creates concise summaries.",
            synopsisPrompt
        );
        
        history.updateSynopsis(newSynopsis);
        
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
     */
    private String generateExchange(ConversationHistory history) {
        List<ConversationHistory.Message> exchanges = 
            history.getExchangesForSynopsis();
        
        if (exchanges.isEmpty()) {
            return history.getSynopsis();
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
        
        // Use ANALYTICAL model (Gemini) - fast and cheap for brief summaries
        return llmClient.callWithHistory(
            ModelType.ANALYTICAL,
            systemPrompt,
            userPrompt,
            messages
        );
    }

    /**
     * Generate detailed synopsis for future reference
     * Uses NARRATIVE model (Claude) - needs comprehensive quality
     */
    public String generateDetailedSynopsis(ConversationHistory history) throws Exception {
        List<ConversationHistory.Message> messages = history.getMessagesForAPI();
        
        String systemPrompt = promptBuilder.buildDetailedSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the detailed setup document.";
        
        // Use NARRATIVE model (Claude) - comprehensive documentation needs quality
        return llmClient.callWithHistory(
            ModelType.NARRATIVE,
            systemPrompt,
            userPrompt,
            messages
        );
    }
}