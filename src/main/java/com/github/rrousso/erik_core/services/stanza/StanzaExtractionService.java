package com.github.rrousso.erik_core.services.stanza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.enums.ModelType;
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
 * BEFORE refactoring: ~600 lines with 5 large apply methods
 * AFTER refactoring: ~100 lines with clean delegation to appliers
 * AFTER JSON util extraction: Even cleaner with no duplicate parsing logic
 * 
 * This is the main orchestrator for Phase 2 (Mid-Stanza Updates).
 * 
 * Process:
 * 1. Build extraction prompt with current state
 * 2. Call Gemini (analytical model) to analyze the exchange
 * 3. Parse JSON response into ExtractionResult (using JsonCleanupUtil)
 * 4. Delegate to ExtractionApplierRegistry to apply all changes
 * 
 * The actual application logic is now in the appliers package:
 * - EventApplier
 * - KnowledgeTransferApplier
 * - SecretRevelationApplier
 * - TensionChangeApplier
 * - CharacterAppearanceApplier
 * 
 * Called after each narrator response in StanzaModeStrategy.
 */
@Service
public class StanzaExtractionService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaExtractionService.class);
    
    private final ExtractionPromptBuilder promptBuilder;
    private final LLMClientService llmClient;
    private final ExtractionApplierRegistry applierRegistry;
    
    public StanzaExtractionService(
            ExtractionPromptBuilder promptBuilder,
            LLMClientService llmClient,
            ExtractionApplierRegistry applierRegistry) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.applierRegistry = applierRegistry;
    }
    
    /**
     * Main entry point: Extract changes from an exchange and update the database.
     * 
     * @param stanza The stanza being played (loaded with all relationships)
     * @param userInput What the user typed
     * @param narratorResponse What the narrator said
     */
    @Transactional
    public void extractAndUpdate(@NonNull Stanza stanza, String userInput, String narratorResponse) {
        log.info("[Extraction] Starting extraction for stanza {}", stanza.getId());
        
        try {
            // 1. Build the extraction prompt
            String prompt = promptBuilder.buildPrompt(stanza, userInput, narratorResponse);
            
            // 2. Call Gemini to analyze the exchange
            log.debug("[Extraction] Calling analytical model");
            String jsonResponse = llmClient.call(ModelType.ANALYTICAL, prompt, "Extract state changes");
            
            // 3. Parse the JSON response using JsonCleanupUtil
            ExtractionResult result = JsonCleanupUtil.parseJson(jsonResponse, ExtractionResult.class);
            
            // 4. Log what we extracted
            if (result.hasAnyChanges()) {
                log.info("[Extraction] Extracted {} total changes: {}", 
                    result.getTotalChangeCount(), result);
            } else {
                log.debug("[Extraction] No changes extracted from this exchange");
                return;
            }
            
            // 5. Apply each type of change using the registry
            // The registry handles the iteration and delegates to individual appliers
            applierRegistry.applyEvents(stanza, result.getEvents());
            applierRegistry.applyFactEstablishments(stanza, result.getFacts());
            applierRegistry.applyKnowledgeTransfers(stanza, result.getKnowledgeTransfers());
            applierRegistry.applySecretRevelations(stanza, result.getSecretRevelations());
            applierRegistry.applyTensionChanges(stanza, result.getTensionChanges());
            applierRegistry.applyCharacterAppearances(stanza, result.getCharacterAppearances());
            
            log.info("[Extraction] Successfully applied all changes");
            
        } catch (Exception e) {
            log.error("[Extraction] Failed to extract/apply changes for stanza " + stanza.getId(), e);
            // Don't rethrow - extraction failure shouldn't break the narrative flow
        }
    }
}