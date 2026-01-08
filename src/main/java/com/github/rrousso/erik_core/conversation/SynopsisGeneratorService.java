package com.github.rrousso.erik_core.conversation;

import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring service for generating rolling synopses
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
     * Generate synopsis for current conversation segment
     */
    public String generateSynopsis(
            ConversationHistory history) throws Exception {
        
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

        String newSynopsis = llmClient.callNarrator(
            "You are a helpful assistant that creates concise summaries.",
            synopsisPrompt
        );
        
        history.updateSynopsis(newSynopsis);
        
        return newSynopsis;
    }
    
    public String generatePauseChanges(
            ConversationHistory history) throws Exception {

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
        
        String userPrompt = "Analyze this conversation and extract changes:\n\n" + 
                           conversationText.toString();
        
        return llmClient.callNarrator(systemPrompt, userPrompt);
    }

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
    
    public String generateQuickSynopsis(ConversationHistory history) throws Exception {
        // Build conversation text from ConversationHistory
    	
    	List<ConversationHistory.Message> messages = 
    	            history.getMessagesForAPI();
    	String systemPrompt = promptBuilder.buildQuickSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the quick narrative summary.";
        
        return llmClient.callWithHistory(systemPrompt, userPrompt, messages);
     }

    public String generateDetailedSynopsis(ConversationHistory history) throws Exception {
        // Build conversation text from ConversationHistory  
    	    	
    	List<ConversationHistory.Message> messages = 
	            history.getMessagesForAPI();
        String systemPrompt = promptBuilder.buildDetailedSynopsisPrompt();
        String userPrompt = "Based on the previous conversations, create the detailed setup document.";
        
        return llmClient.callWithHistory(systemPrompt, userPrompt, messages);
    }
}
