package com.github.rrousso.erik_core.services.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.domain.models.SessionContext;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.services.session.SessionAssemblerService;
import com.github.rrousso.erik_core.services.session.SynopsisGeneratorService;

/**
 * Unified service for handling LLM conversations in both VOID and STANZA modes.
 * 
 * This service encapsulates the common pattern:
 * 1. Assemble context (based on mode)
 * 2. Build system prompt (based on mode)
 * 3. Call LLM
 * 4. Update conversation history (based on mode)
 * 5. Generate synopsis (only for STANZA mode)
 * 
 * By centralizing this logic, we eliminate duplication across strategies
 * and provide a single point of maintenance for conversation flow.
 */
@Service
public class ConversationService {
    
    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    
    /**
     * Conversation mode determines which context, prompt, and history to use.
     */
    public enum ConversationMode {
        /** VOID mode: User talks to Erik for planning and reflection */
        VOID,
        
        /** STANZA mode: User interacts with the Narrator during active storytelling */
        STANZA
    }
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final SessionAssemblerService sessionAssembler;
    private final SynopsisGeneratorService synopsisGenerator;
    
    public ConversationService(
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            SessionAssemblerService sessionAssembler,
            SynopsisGeneratorService synopsisGenerator) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.sessionAssembler = sessionAssembler;
        this.synopsisGenerator = synopsisGenerator;
    }
    
    /**
     * Conduct a conversation with the LLM in the specified mode.
     * 
     * This method handles the complete conversation flow:
     * - Assembles the appropriate context
     * - Builds the appropriate system prompt
     * - Calls the LLM
     * - Updates the appropriate conversation history
     * - Generates synopsis (if in STANZA mode)
     * 
     * @param mode The conversation mode (VOID or STANZA)
     * @param state The current session state
     * @param userInput The user's input text
     * @return The LLM's response
     * @throws Exception if any step of the conversation fails
     */
    public String converse(ConversationMode mode, SessionState state, String userInput) throws Exception {
        log.debug("Starting conversation in {} mode", mode);
        
        // 1. Assemble context based on mode
        SessionContext context = (mode == ConversationMode.VOID) 
            ? sessionAssembler.assembleForVoid(state)
            : sessionAssembler.assembleForStanza(state);
        
        // 2. Build system prompt based on mode
        String systemPrompt = (mode == ConversationMode.VOID)
            ? promptBuilder.buildVoidPromptFromContext(context)
            : promptBuilder.buildStanzaPromptFromContext(context);
        
        // 3. Call LLM (same model for both modes)
        String response = llmClient.call(ModelType.NARRATIVE, systemPrompt, userInput);
        
        // 4. Get the appropriate history based on mode
        ConversationHistory history = (mode == ConversationMode.VOID)
            ? state.getVoidHistory()
            : state.getStanzaHistory();
        
        // 5. Update conversation history
        history.addUserMessage(userInput);
        history.addAssistantMessage(response);
        
        // 6. Generate synopsis (only for STANZA mode)
        if (mode == ConversationMode.STANZA) {
            try {
                synopsisGenerator.generateSynopsis(state.getStanzaHistory());
            } catch (Exception e) {
                log.warn("Failed to generate synopsis", e);
                // Don't fail the whole conversation if synopsis generation fails
            }
        }
        
        log.debug("Conversation completed successfully in {} mode", mode);
        return response;
    }
    
    /**
     * Convenience method for VOID mode conversations (talking to Erik).
     */
    public String converseWithErik(SessionState state, String userInput) throws Exception {
        return converse(ConversationMode.VOID, state, userInput);
    }
    
    /**
     * Convenience method for STANZA mode conversations (talking to Narrator).
     */
    public String converseWithNarrator(SessionState state, String userInput) throws Exception {
        return converse(ConversationMode.STANZA, state, userInput);
    }
}