package com.github.rrousso.erik_core.services.stanza;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.config.ConfigService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.PromptLoaderService;

/**
 * Service for initializing a stanza from the planning conversation.
 * 
 * This is a ONE-TIME call per stanza that:
 * 1. Takes the planning conversation with Erik
 * 2. Calls the analytical LLM with the initialization prompt
 * 3. Parses the JSON response into InitializedStanza
 * 4. Returns the fully populated stanza state
 * 
 * The InitializedStanza replaces StanzaMetadata and provides:
 * - Tiered character lists with knowledge boundaries
 * - Narrative tensions with pressure tracking
 * - World context and rules
 */
@Service
public class StanzaInitializationService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaInitializationService.class);
    
    private static final String DEBUG_FILE = "user_data/initialization_result.txt";
    
    private final LLMClientService llmClient;
    private final PromptLoaderService promptLoader;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    
    public StanzaInitializationService(
            LLMClientService llmClient,
            PromptLoaderService promptLoader,
            ConfigService configService) {
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        
        log.info("StanzaInitializationService initialized");
    }
    
    /**
     * Initialize a stanza from the planning conversation.
     * 
     * @param voidHistory The conversation history from planning with Erik
     * @param loadedStanza Optional stanza loaded via /load command (for continuation)
     * @return InitializedStanza with full character roster and tensions
     * @throws Exception if LLM call or parsing fails
     */
    public InitializedStanza initializeFromPlanning(ConversationHistory voidHistory, Stanza loadedStanza) throws Exception {
        log.info("[Initialization] Starting stanza initialization from planning conversation");
        
        // Build the input for the initialization prompt
        String planningContext = buildPlanningContext(voidHistory, loadedStanza);
        String userPersona = configService.getUserPersona();
        
        // Load the initialization prompt
        String initPrompt = promptLoader.load("architect/initialization_prompt.txt");
        
        // Build the full prompt
        String fullPrompt = buildFullPrompt(initPrompt, userPersona, planningContext);
        
        log.info("[Initialization] Calling analytical model for initialization...");
        log.debug("[Initialization] Prompt length: {} chars", fullPrompt.length());
        
        // Call the analytical model
        String response = llmClient.call(
            ModelType.ANALYTICAL,
            "You are a stanza initialization architect. Output ONLY valid JSON.",
            fullPrompt
        );
        
        log.info("[Initialization] Received response ({} chars)", response.length());
        
        // Clean up the response (remove markdown fences if present)
        String cleanJson = cleanJsonResponse(response);
        
        // Save to debug file
        saveToDebugFile(cleanJson);
        
        // Parse the JSON
        InitializedStanza stanza = parseResponse(cleanJson);
        
        log.info("[Initialization] Successfully parsed initialization:");
        log.info("  - World: {}", stanza.getWorldIdentifier());
        log.info("  - Explicit characters: {}", stanza.getExplicitCharacters().size());
        log.info("  - Likely characters: {}", stanza.getLikelyCharacters().size());
        log.info("  - Background characters: {}", stanza.getBackgroundCharacters().size());
        log.info("  - Initial tensions: {}", stanza.getInitialTensions().size());
        
        // Check for clarifications
        if (stanza.needsClarification()) {
            log.warn("[Initialization] Clarifications needed: {}", stanza.getClarificationsNeeded());
        }
        
        return stanza;
    }
    
    /**
     * Build the planning context from conversation history
     */
    private String buildPlanningContext(ConversationHistory history, Stanza loadedStanza) {
        StringBuilder sb = new StringBuilder();
        
        // If there's a loaded stanza, include it FIRST
        if (loadedStanza != null) {
            sb.append("=== LOADED STANZA FOR REFERENCE/CONTINUATION ===\n\n");
            sb.append("The user has loaded this stanza from the database:\n\n");
            sb.append("ID: ").append(loadedStanza.getId()).append("\n");
            sb.append("Setting: ").append(loadedStanza.getSetting()).append("\n");
            sb.append("Premise: ").append(loadedStanza.getPremise()).append("\n");
            sb.append("World: ").append(loadedStanza.getWorldIdentifier()).append("\n\n");
            
            // Include characters from the loaded stanza
            if (!loadedStanza.getCharacters().isEmpty()) {
                sb.append("Characters in loaded stanza:\n");
                for (var character : loadedStanza.getCharacters()) {
                    if (!character.isUser()) {
                        sb.append("- ").append(character.getName());
                        if (character.getPublicRole() != null && !character.getPublicRole().isEmpty()) {
                            sb.append(" (").append(character.getPublicRole()).append(")");
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
            
            // Include synopsis if available
            if (loadedStanza.getQuickSynopsis() != null && !loadedStanza.getQuickSynopsis().isEmpty()) {
                sb.append("What happened in the loaded stanza:\n");
                sb.append(loadedStanza.getQuickSynopsis()).append("\n\n");
            }
            
            sb.append("IMPORTANT: If the user wants to continue or build on this stanza, ");
            sb.append("use the SAME characters, SAME setting, SAME world. ");
            sb.append("Do NOT create a completely new scenario unless explicitly asked.\n\n");
            sb.append("=== END LOADED STANZA ===\n\n");
        }
        
        sb.append("=== PLANNING CONVERSATION ===\n\n");
        
        for (ConversationHistory.Message msg : history.getAllMessages()) {
            String role = "user".equals(msg.getRole()) ? "USER" : "ERIK";
            sb.append(role).append(": ").append(msg.getContent()).append("\n\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Build the full prompt with user persona and planning context
     */
    private String buildFullPrompt(String initPrompt, String userPersona, String planningContext) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(initPrompt);
        sb.append("\n\n");
        sb.append("---\n\n");
        sb.append("## ACTUAL INPUT FOR THIS STANZA\n\n");
        sb.append("**User Persona:**\n");
        sb.append(userPersona);
        sb.append("\n\n");
        sb.append("**Planning Conversation:**\n");
        sb.append(planningContext);
        sb.append("\n\n");
        sb.append("Now output the initialization JSON:");
        
        return sb.toString();
    }
    
    /**
     * Clean up JSON response (remove markdown fences, trim)
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        
        // Remove ```json and ``` if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    /**
     * Parse the JSON response into InitializedStanza
     */
    private InitializedStanza parseResponse(String json) throws Exception {
        try {
            return objectMapper.readValue(json, InitializedStanza.class);
        } catch (Exception e) {
            log.error("[Initialization] Failed to parse JSON response: {}", e.getMessage());
            log.debug("[Initialization] Raw JSON:\n{}", json);
            throw new RuntimeException("Failed to parse initialization response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Save result to debug file for inspection
     */
    private void saveToDebugFile(String json) {
        try {
            Path filePath = Paths.get(DEBUG_FILE);
            Files.createDirectories(filePath.getParent());
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder output = new StringBuilder();
            output.append("// Initialization Result - ").append(timestamp).append("\n");
            output.append("// This file is for debugging - inspect the parsed JSON\n\n");
            output.append(json);
            
            Files.writeString(filePath, output.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            
            log.info("[Initialization] Saved result to {}", filePath.toAbsolutePath());
            
        } catch (IOException e) {
            log.warn("[Initialization] Failed to save debug file: {}", e.getMessage());
        }
    }
    
    /**
     * Validate an InitializedStanza has minimum required data
     */
    public boolean isValid(InitializedStanza stanza) {
        if (stanza == null) return false;
        if (stanza.getUserCharacter() == null) return false;
        if (stanza.getWorldIdentifier() == null || stanza.getWorldIdentifier().isEmpty()) return false;
        return true;
    }
}