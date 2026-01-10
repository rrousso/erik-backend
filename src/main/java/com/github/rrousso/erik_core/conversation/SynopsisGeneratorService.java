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

        System.out.println("\n========== ROLLING SYNOPSIS GENERATION START ==========");

        String exchangeText = generateExchange(history);
        System.out.println("[Synopsis] Exchange text to be condensed (" + exchangeText.length() + " chars):");
        System.out.println("--- BEGIN EXCHANGE TEXT ---");
        System.out.println(exchangeText);
        System.out.println("--- END EXCHANGE TEXT ---");

        String previousSynopsis = history.getSynopsis();
        System.out.println("[Synopsis] Previous synopsis (" + previousSynopsis.length() + " chars):");
        System.out.println("--- BEGIN PREVIOUS SYNOPSIS ---");
        System.out.println(previousSynopsis.isEmpty() ? "[No previous synopsis]" : previousSynopsis);
        System.out.println("--- END PREVIOUS SYNOPSIS ---");

        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous synopsis]";
        }

        System.out.println("[System] Generating synopsis...");

        String synopsisPrompt = "Condense the following conversation into a brief synopsis. " +
            "Extract key events, decisions, and important details. " +
            "Previous synopsis: " + previousSynopsis + "\n\n" +
            "Recent exchanges:\n" + exchangeText + "\n\n" +
            "Create an updated synopsis:";

        System.out.println("[Synopsis] Full prompt being sent to LLM:");
        System.out.println("--- BEGIN SYNOPSIS PROMPT ---");
        System.out.println(synopsisPrompt);
        System.out.println("--- END SYNOPSIS PROMPT ---");

        // Use NARRATIVE model for rolling synopsis (needs quality)
        String newSynopsis = llmClient.call(
            ModelType.NARRATIVE,
            "You are a helpful assistant that creates concise summaries.",
            synopsisPrompt
        );

        System.out.println("[Synopsis] Generated new synopsis (" + newSynopsis.length() + " chars):");
        System.out.println("--- BEGIN NEW SYNOPSIS ---");
        System.out.println(newSynopsis);
        System.out.println("--- END NEW SYNOPSIS ---");

        history.updateSynopsis(newSynopsis);
        System.out.println("========== ROLLING SYNOPSIS GENERATION END ==========\n");

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
        System.out.println("\n========== QUICK SYNOPSIS GENERATION START ==========");

        List<ConversationHistory.Message> messages = history.getMessagesForAPI();
        System.out.println("[QuickSynopsis] Message count: " + messages.size());
        System.out.println("[QuickSynopsis] Messages being sent:");
        for (int i = 0; i < messages.size(); i++) {
            ConversationHistory.Message msg = messages.get(i);
            System.out.println("  [" + i + "] " + msg.getRole() + ": " +
                (msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent()));
        }

        String systemPrompt = promptBuilder.buildQuickSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the quick narrative summary.";

        System.out.println("[QuickSynopsis] System prompt length: " + systemPrompt.length() + " chars");
        System.out.println("[QuickSynopsis] User prompt: " + userPrompt);

        // Use ANALYTICAL model (Gemini) - fast and cheap for brief summaries
        String result = llmClient.callWithHistory(
            ModelType.ANALYTICAL,
            systemPrompt,
            userPrompt,
            messages
        );

        System.out.println("[QuickSynopsis] Generated synopsis (" + result.length() + " chars):");
        System.out.println("--- BEGIN QUICK SYNOPSIS ---");
        System.out.println(result);
        System.out.println("--- END QUICK SYNOPSIS ---");
        System.out.println("========== QUICK SYNOPSIS GENERATION END ==========\n");

        return result;
    }

    /**
     * Generate detailed synopsis for future reference
     * Uses NARRATIVE model (Claude) - needs comprehensive quality
     */
    public String generateDetailedSynopsis(ConversationHistory history) throws Exception {
        System.out.println("\n========== DETAILED SYNOPSIS GENERATION START ==========");

        List<ConversationHistory.Message> messages = history.getMessagesForAPI();
        System.out.println("[DetailedSynopsis] Message count: " + messages.size());
        System.out.println("[DetailedSynopsis] Messages being sent:");
        for (int i = 0; i < messages.size(); i++) {
            ConversationHistory.Message msg = messages.get(i);
            System.out.println("  [" + i + "] " + msg.getRole() + ": " +
                (msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "..." : msg.getContent()));
        }

        String systemPrompt = promptBuilder.buildDetailedSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the detailed setup document.";

        System.out.println("[DetailedSynopsis] System prompt length: " + systemPrompt.length() + " chars");
        System.out.println("[DetailedSynopsis] User prompt: " + userPrompt);

        // Use NARRATIVE model (Claude) - comprehensive documentation needs quality
        String result = llmClient.callWithHistory(
            ModelType.NARRATIVE,
            systemPrompt,
            userPrompt,
            messages
        );

        System.out.println("[DetailedSynopsis] Generated synopsis (" + result.length() + " chars):");
        System.out.println("--- BEGIN DETAILED SYNOPSIS ---");
        System.out.println(result);
        System.out.println("--- END DETAILED SYNOPSIS ---");
        System.out.println("========== DETAILED SYNOPSIS GENERATION END ==========\n");

        return result;
    }
}