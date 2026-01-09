package com.github.rrousso.erik_core.stanza;

import com.github.rrousso.erik_core.conversation.ConversationHistory;
import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.llm.ModelType;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring service for extracting stanza setup details from Void conversation
 */
@Service
public class StanzaExtractorService {
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    
    public StanzaExtractorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }
    
    /**
     * Extract stanza setup from conversation history
     */
    public StanzaSetup extract(ConversationHistory history) throws Exception {
        System.out.println("\n[Extractor] Asking Erik to structure the stanza...");
        
        List<ConversationHistory.Message> voidConvo = history.getConversationForExtraction();
        
        String conversationContext = buildConversationContext(voidConvo);
        
        // Use the extraction prompt from the prompt builder
        String extractionSystemPrompt = promptBuilder.buildExtractionPrompt();
        
        String response = llmClient.call(
                ModelType.ANALYTICAL,  // Use Gemini
                extractionSystemPrompt,
                conversationContext
            );
        
        StanzaSetup setup = StanzaSetup.parseFromErikResponse(response);
        
        return setup;
    }
    
    private String buildConversationContext(List<ConversationHistory.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here is our planning conversation:\n\n");
        
        for (ConversationHistory.Message msg : messages) {
            sb.append(msg.getRole().toUpperCase()).append(": ");
            sb.append(msg.getContent());
            sb.append("\n\n");
        }
        
        return sb.toString();
    }
}