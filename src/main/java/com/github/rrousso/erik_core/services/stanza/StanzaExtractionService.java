package com.github.rrousso.erik_core.services.stanza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ExtractionConfig;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.dto.extraction.ExtractionResult;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.ExtractionPromptBuilder;
import com.github.rrousso.erik_core.services.stanza.appliers.ExtractionApplierRegistry;
import com.github.rrousso.erik_core.util.JsonCleanupUtil;

import jakarta.transaction.Transactional;

/**
 * Service responsible for extracting state changes from narrative exchanges
 * and updating the database accordingly.
 * 
 * EVOLUTION:
 * - v1: ~600 lines with 5 large apply methods
 * - v2: ~100 lines with clean delegation to appliers
 * - v3: JSON util extraction for cleaner parsing
 * - v4: Owns extraction frequency logic - callers use processExtraction()
 * - v5: Conversation context integration - sees exchanges since last extraction
 * 
 * This is the main orchestrator for Phase 2 (Mid-Stanza Updates).
 * 
 * Process:
 * 1. Check if extraction should happen (based on config)
 * 2. Build extraction prompt with conversation context
 * 3. Call Gemini (analytical model) to analyze exchanges since last extraction
 * 4. Parse JSON response into ExtractionResult
 * 5. Delegate to ExtractionApplierRegistry to apply all changes
 * 
 * The actual application logic is in the appliers package:
 * - EventApplier, KnowledgeTransferApplier, SecretRevelationApplier,
 *   TensionChangeApplier, CharacterAppearanceApplier
 */
@Service
public class StanzaExtractionService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaExtractionService.class);
    
    private final ExtractionPromptBuilder promptBuilder;
    private final LLMClientService llmClient;
    private final ExtractionApplierRegistry applierRegistry;
    private final ExtractionConfig extractionConfig;
    
    public StanzaExtractionService(
            ExtractionPromptBuilder promptBuilder,
            LLMClientService llmClient,
            ExtractionApplierRegistry applierRegistry,
            ExtractionConfig extractionConfig) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.applierRegistry = applierRegistry;
        this.extractionConfig = extractionConfig;
    }
    
    /**
     * Process extraction based on configured frequency and flags.
     * 
     * This is the main entry point for regular extraction during stanza flow.
     * Returns true if extraction was performed, false if skipped.
     * 
     * @param stanza The stanza being played (loaded with all relationships)
     * @param history The conversation history
     * @param exchangeNumber Current exchange number (1-indexed)
     * @param isFirstExchange Whether this is the opening narration
     * @param isFinalExchange Whether this is the closing narration
     * @return true if extraction was performed, false if skipped
     */
    @Transactional
    public boolean processExtraction(
            @NonNull Stanza stanza, 
            ConversationHistory history,
            int exchangeNumber,
            boolean isFirstExchange,
            boolean isFinalExchange) {
        
        // Check if we should extract for this exchange
        boolean shouldExtract = extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange);
        
        if (!shouldExtract) {
            log.debug("[Extraction] Skipping extraction for exchange {} (frequency: {})", 
                exchangeNumber, extractionConfig.getFrequency());
            return false;
        }
        
        log.info("[Extraction] Processing extraction for exchange {} (frequency: {}, history size: {})", 
            exchangeNumber, extractionConfig.getFrequency(), history.getCurrentHistorySize());
        
        // Perform extraction
        performExtraction(stanza, history);
        return true;
    }
    
    /**
     * Force extraction regardless of frequency configuration.
     * 
     * Used for critical moments like beat boundaries where we must capture
     * the current state even if it's not normally scheduled.
     * 
     * @param stanza The stanza being played (loaded with all relationships)
     * @param history The conversation history
     * @return true if extraction succeeded, false if it failed
     */
    @Transactional
    public boolean forceExtraction(@NonNull Stanza stanza, ConversationHistory history) {
        int exchangeNumber = stanza.getCurrentExchange();
        
        log.info("[Extraction] FORCED extraction for exchange {} (history size: {})", 
            exchangeNumber, history.getCurrentHistorySize());
        
        return performExtraction(stanza, history);
    }
    
    /**
     * Internal method: Actually perform the extraction and database updates.
     * 
     * This gets conversation context (last N exchanges, or synopsis + recent if needed)
     * and sends it to the extraction LLM for analysis.
     * 
     * @param stanza The stanza being played (loaded with all relationships)
     * @param history The conversation history
     * @return true if extraction succeeded, false if it failed
     */
    private boolean performExtraction(@NonNull Stanza stanza, ConversationHistory history) {
        try {
            // 1. Build the extraction prompt (with conversation context)
            int frequency = extractionConfig.getFrequency();
            String prompt = promptBuilder.buildPrompt(stanza, history, frequency);
            
            // 2. Call Gemini to analyze the exchanges
            log.debug("[Extraction] Calling analytical model");
            String jsonResponse = llmClient.call(ModelType.ANALYTICAL, prompt, "Extract state changes");
            
            // 3. Parse the JSON response using JsonCleanupUtil
            ExtractionResult result = JsonCleanupUtil.parseJson(jsonResponse, ExtractionResult.class);
            
            // 4. Log what we extracted
            if (result.hasAnyChanges()) {
                log.info("[Extraction] Extracted {} total changes: {}", 
                    result.getTotalChangeCount(), result);
            } else {
                log.debug("[Extraction] No changes extracted from these exchanges");
                return true; // No changes is still a success
            }
            
            // 5. Apply each type of change using the registry
            // The registry handles the iteration and delegates to individual appliers
            applierRegistry.applyEvents(stanza, result.getEvents());
            applierRegistry.applyFactDiscoveries(stanza, result.getFactDiscoveries());
            applierRegistry.applySecretRevelations(stanza, result.getSecretRevelations());
            applierRegistry.applyTensionChanges(stanza, result.getTensionChanges());
            applierRegistry.applyCharacterAppearances(stanza, result.getCharacterAppearances());
            
            log.info("[Extraction] Successfully applied all changes");
            return true;
            
        } catch (Exception e) {
            log.error("[Extraction] Failed to extract/apply changes for stanza " + stanza.getId(), e);
            // Don't rethrow - extraction failure shouldn't break the narrative flow
            return false;
        }
    }
}