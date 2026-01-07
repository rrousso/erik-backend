package com.github.rrousso.erik_core.conversation;

import com.github.rrousso.erik_core.llm.LLMClientService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring service for generating rolling synopses
 */
@Service
public class SynopsisGeneratorService {
    
    private final LLMClientService llmClient;
    
    public SynopsisGeneratorService(LLMClientService llmClient) {
        this.llmClient = llmClient;
    }
    
    /**
     * Generate synopsis for current conversation segment
     */
    public String generateSynopsis(
            ConversationHistory history, 
            boolean isStanzaMode) throws Exception {
        
        if (!history.shouldGenerateSynopsis(isStanzaMode)) {
            return history.getSynopsis(isStanzaMode);
        }
        
        List<ConversationHistory.Message> exchanges = 
            history.getExchangesForSynopsis(isStanzaMode);
        
        if (exchanges.isEmpty()) {
            return history.getSynopsis(isStanzaMode);
        }
        
        // Build exchange text
        StringBuilder exchangeText = new StringBuilder();
        for (ConversationHistory.Message msg : exchanges) {
            exchangeText.append(msg.getRole().toUpperCase())
                       .append(": ")
                       .append(msg.getContent())
                       .append("\n\n");
        }
        
        System.out.println("[DEBUG] Exchange text to condense:");
        System.out.println(exchangeText.toString());
        
        String previousSynopsis = history.getSynopsis(isStanzaMode);
        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous synopsis]";
        }
        
        System.out.println("[System] Generating synopsis...");
        
        String synopsisPrompt = "Condense the following conversation into a brief synopsis. " +
            "Extract key events, decisions, and important details. " +
            "Previous synopsis: " + previousSynopsis + "\n\n" +
            "Recent exchanges:\n" + exchangeText.toString() + "\n\n" +
            "Create an updated synopsis:";

        String newSynopsis = llmClient.callNarrator(
            "You are a helpful assistant that creates concise summaries.",
            synopsisPrompt
        );
        
        history.updateSynopsis(newSynopsis, isStanzaMode);
        
        return newSynopsis;
    }
}
