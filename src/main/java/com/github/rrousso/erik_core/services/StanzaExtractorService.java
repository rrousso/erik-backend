package com.github.rrousso.erik_core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.entities.ConversationHistory;
import com.github.rrousso.erik_core.entities.ModelType;
import com.github.rrousso.erik_core.entities.StanzaSetup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for extracting stanza setup from conversation history.
 * Handles both LLM communication and JSON response parsing.
 * 
 * ENHANCED: Now parses userBackstory field for private information
 */
@Service
public class StanzaExtractorService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaExtractorService.class);
    
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private static final String EXTRACTED_STANZA_DEBUG_FILE = "user_data/extracted_stanza.txt";
    
    public StanzaExtractorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
    }
    
    /**
     * Extract stanza setup from conversation history
     */
    public StanzaSetup extract(ConversationHistory history) throws Exception {
        log.info("[Extractor] Asking LLM to structure the stanza...");
        
        List<ConversationHistory.Message> voidConvo = history.getConversationForExtraction();
        String conversationContext = buildConversationContext(voidConvo);
        String extractionSystemPrompt = promptBuilder.buildExtractionPrompt();
        
        String response = llmClient.call(
            ModelType.ANALYTICAL,
            extractionSystemPrompt,
            conversationContext
        );
        
        // Parse the JSON response
        StanzaSetup setup = parseJsonResponse(response);
        
        log.info("[Extractor] Extraction complete");
        log.info("[Extractor] Premise: {}", setup.getPremise());
        log.info("[Extractor] User Role (PUBLIC): {}", setup.getUserRole());
        log.info("[Extractor] User Backstory (PRIVATE): {}", 
            setup.getUserBackstory().isEmpty() ? "[none]" : "[present - " + setup.getUserBackstory().length() + " chars]");
        
        saveSynopsisToFile(response,EXTRACTED_STANZA_DEBUG_FILE);
        return setup;
    }
    
    /**
     * Parse JSON response from LLM into StanzaSetup object
     * ENHANCED: Now parses userBackstory field
     */
    private StanzaSetup parseJsonResponse(String response) {
        StanzaSetup setup = new StanzaSetup();
        
        // Clean up response (remove markdown code fences)
        String json = response.replaceAll("```json", "").replaceAll("```", "").trim();
        
        // Extract all fields
        setup.setSetting(extractField(json, "setting"));
        setup.setPremise(extractField(json, "premise"));
        setup.setUserRole(extractField(json, "userRole"));
        setup.setUserBackstory(extractField(json, "userBackstory"));  // NEW - private backstory
        setup.setTone(extractField(json, "tone"));
        setup.setCharacters(extractArray(json, "characters"));
        setup.setSpecialRules(extractArray(json, "specialRules"));
        
        return setup;
    }
    
    /**
     * Extract a string field from JSON
     */
    private String extractField(String json, String fieldName) {
        int fieldStart = json.indexOf("\"" + fieldName + "\"");
        if (fieldStart == -1) {
            return "";
        }
        
        int colonPos = json.indexOf(":", fieldStart);
        if (colonPos == -1) {
            return "";
        }
        
        int valueStart = json.indexOf("\"", colonPos);
        if (valueStart == -1) {
            return "";
        }
        
        int valueEnd = valueStart + 1;
        while (valueEnd < json.length()) {
            if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') {
                break;
            }
            valueEnd++;
        }
        
        if (valueEnd >= json.length()) {
            return "";
        }
        
        return json.substring(valueStart + 1, valueEnd)
            .replace("\\n", "\n")
            .replace("\\\"", "\"");
    }
    
    /**
     * Extract an array of strings from JSON
     */
    private List<String> extractArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        
        int fieldStart = json.indexOf("\"" + fieldName + "\"");
        if (fieldStart == -1) {
            return result;
        }
        
        int arrayStart = json.indexOf("[", fieldStart);
        if (arrayStart == -1) {
            return result;
        }
        
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd == -1) {
            return result;
        }
        
        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        result = parseArrayItems(arrayContent);
        
        return result;
    }
    
    /**
     * Find the closing bracket for an array
     */
    private int findMatchingBracket(String json, int openBracket) {
        int depth = 1;
        for (int i = openBracket + 1; i < json.length(); i++) {
            if (json.charAt(i) == '[') {
                depth++;
            } else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Parse individual items from a JSON array
     */
    private List<String> parseArrayItems(String arrayContent) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (c == ',' && !inString) {
                String item = current.toString().trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
                current = new StringBuilder();
                continue;
            }
            
            if (inString) {
                current.append(c);
            }
        }
        
        String item = current.toString().trim();
        if (!item.isEmpty()) {
            items.add(item);
        }
        
        return items;
    }
    
    /**
     * Build conversation context string from messages
     */
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
    
    /**
     * ENHANCEMENT: Save synopsis to file for debugging
     * This allows you to check the synopsis even if console output is truncated
     */
    private void saveSynopsisToFile(String synopsis, String path) {
        try {
            Path filePath = Paths.get(path);
            
            // Ensure parent directory exists
            Files.createDirectories(filePath.getParent());
            
            // Create formatted output with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder output = new StringBuilder();
            output.append("=".repeat(80)).append("\n");
            output.append("EXTRACTED STANZA").append("\n");
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