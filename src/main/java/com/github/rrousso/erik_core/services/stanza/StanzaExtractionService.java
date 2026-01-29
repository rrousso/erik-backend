package com.github.rrousso.erik_core.services.stanza;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.dto.extraction.CharacterAppearance;
import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.dto.extraction.ExtractionResult;
import com.github.rrousso.erik_core.dto.extraction.KnowledgeTransfer;
import com.github.rrousso.erik_core.dto.extraction.SecretRevelation;
import com.github.rrousso.erik_core.dto.extraction.TensionChange;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.ExtractionPromptBuilder;

import jakarta.transaction.Transactional;

/**
 * Service responsible for extracting state changes from narrative exchanges
 * and updating the database accordingly.
 * 
 * This is the main orchestrator for Phase 2 (Mid-Stanza Updates).
 * 
 * Process:
 * 1. Load stanza from database (with all relationships)
 * 2. Build extraction prompt with current state
 * 3. Call Gemini to analyze the exchange
 * 4. Parse JSON response into ExtractionResult
 * 5. Apply each type of change to the database
 * 
 * Called after each narrator response in SessionFlowService.
 */
@Service
public class StanzaExtractionService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaExtractionService.class);
    
    private final ExtractionPromptBuilder promptBuilder;
    private final LLMClientService llmClient;
    private final ObjectMapper objectMapper;
    
    public StanzaExtractionService(
            ExtractionPromptBuilder promptBuilder,
            LLMClientService llmClient) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Main entry point: Extract changes from an exchange and update the database.
     * 
     * @param stanzaId The stanza being played
     * @param userInput What the user typed
     * @param narratorResponse What the narrator said
     */
    @Transactional
    public void extractAndUpdate(@NonNull Stanza stanza, String userInput, String narratorResponse)  {
        log.info("[Extraction] Starting extraction for stanza {}", stanza.getId());
        
        try {            
            // 1. Build the extraction prompt
            String prompt = promptBuilder.buildPrompt(stanza, userInput, narratorResponse);
            
            // 2. Call Gemini to analyze the exchange
            log.debug("[Extraction] Calling analytical model");
            String jsonResponse = llmClient.call(ModelType.ANALYTICAL, prompt, "Extract state changes");
            
            // 3. Parse the JSON response
            ExtractionResult result = parseExtractionResult(jsonResponse);
            
            // 4. Log what we extracted
            if (result.hasAnyChanges()) {
                log.info("[Extraction] Extracted {} total changes: {}", 
                    result.getTotalChangeCount(), result);
            } else {
                log.debug("[Extraction] No changes extracted from this exchange");
                return;
            }
            
            // 6. Apply each type of change to the database
            applyEvents(stanza, result.getEvents());
            applyKnowledgeTransfers(stanza, result.getKnowledgeTransfers());
            applySecretRevelations(stanza, result.getSecretRevelations());
            applyTensionChanges(stanza, result.getTensionChanges());
            applyCharacterAppearances(stanza, result.getCharacterAppearances());
            
            log.info("[Extraction] Successfully applied all changes");
            
        } catch (Exception e) {
            log.error("[Extraction] Failed to extract/apply changes for stanza " + stanza.getId(), e);
            // Don't rethrow - extraction failure shouldn't break the narrative flow
        }
    }
    
    // ========== PARSING ==========
    
    /**
     * Parse the JSON response from Gemini into ExtractionResult.
     * Handles cleanup of markdown code fences if present.
     */
    private ExtractionResult parseExtractionResult(String jsonResponse) throws Exception {
        // Clean up potential markdown code fences
        String cleaned = jsonResponse.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        
        // Parse JSON
        return objectMapper.readValue(cleaned, ExtractionResult.class);
    }
    
    // ========== APPLICATION METHODS (STUBS FOR NOW) ==========
    
    /**
     * Apply extracted events to the database.
     * Creates StanzaEvent entries.
     * 
     * Process:
     * 1. Loop through each extracted event
     * 2. Create a new StanzaEvent entity
     * 3. Set the description (automatically truncated to 280 chars)
     * 4. Set the beat and exchange numbers from the stanza
     * 5. Convert charactersInvolved list to comma-separated string
     * 6. Set isMajor flag based on significance
     * 7. Add to stanza's events list (cascade will save it)
     */
    private void applyEvents(Stanza stanza, java.util.List<EventExtraction> events) {
        if (events.isEmpty()) {
            return;
        }
        
        log.info("[Extraction] Applying {} events", events.size());
        
        for (EventExtraction extraction : events) {
            // Validate description length before creating entity
            if (extraction.getDescription() != null && extraction.getDescription().length() > 280) {
                log.warn("[Extraction] Event description exceeds 280 characters and will be truncated: '{}'", 
                    extraction.getDescription().substring(0, Math.min(100, extraction.getDescription().length())) + "...");
                log.warn("[Extraction] Consider adjusting extraction prompt to generate shorter descriptions");
            }
            
            // Create new event entity
            StanzaEvent event = new StanzaEvent();
            
            // Set the stanza relationship
            event.setStanza(stanza);
            
            // Set description (constructor would truncate, but we use setter here)
            event.setDescription(extraction.getDescription());
            
            // Set when this happened (beat and exchange)
            event.setBeatNumber(stanza.getCurrentBeat());
            event.setExchangeNumber(stanza.getCurrentExchange());
            
            // Convert List<String> to comma-separated string for involved characters
            if (extraction.getCharactersInvolved() != null && !extraction.getCharactersInvolved().isEmpty()) {
                String involvedCharacters = String.join(",", extraction.getCharactersInvolved());
                event.setInvolvedCharacters(involvedCharacters);
            }
            
            // Set major flag based on significance
            event.setMajor(extraction.isMajor());
            
            // Add to stanza's events list
            // Because of cascade = CascadeType.ALL, this will be saved when the transaction commits
            stanza.getEvents().add(event);
            
            log.debug("[Extraction] Created event: {} (exchange {}, {})", 
                extraction.getDescription(), 
                stanza.getCurrentExchange(),
                extraction.getSignificance());
        }
        
        log.info("[Extraction] Successfully created {} event entries", events.size());
    }
    
    /**
     * Apply knowledge transfers to the database.
     * Creates CharacterKnowledge entries.
     * 
     * Process:
     * 1. Find the character by name (from the loaded stanza)
     * 2. Find or create the Fact for what they learned
     * 3. Create CharacterKnowledge linking character to fact
     * 4. Set how they learned it
     * 5. Add to character's knownFacts collection
     */
    private void applyKnowledgeTransfers(Stanza stanza, java.util.List<KnowledgeTransfer> transfers) {
        if (transfers.isEmpty()) {
            return;
        }
        
        log.info("[Extraction] Applying {} knowledge transfers", transfers.size());
        
        for (KnowledgeTransfer transfer : transfers) {
            // Validate lengths
            if (transfer.getWhatTheyLearned() != null && transfer.getWhatTheyLearned().length() > 200) {
                log.warn("[Extraction] Knowledge description exceeds recommended 200 characters: '{}'",
                    transfer.getWhatTheyLearned().substring(0, Math.min(100, transfer.getWhatTheyLearned().length())) + "...");
            }
            
            // 1. Find the character by name (case-insensitive)
            Optional<com.github.rrousso.erik_core.persistence.entities.StanzaCharacter> charOpt = 
                stanza.getCharacters().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(transfer.getCharacterName()))
                    .findFirst();
            
            if (!charOpt.isPresent()) {
                log.warn("[Extraction] Character '{}' not found in stanza - skipping knowledge transfer", 
                    transfer.getCharacterName());
                continue;
            }
            
            com.github.rrousso.erik_core.persistence.entities.StanzaCharacter character = charOpt.get();
            
            // 2. Find or create the Fact
            com.github.rrousso.erik_core.persistence.entities.Fact fact = 
                new com.github.rrousso.erik_core.persistence.entities.Fact();
            
            fact.setStanza(stanza);
            fact.setKind("OBSERVED"); // Default kind - could be smarter based on context
            fact.setPredicate(transfer.getWhatTheyLearned());
            fact.setFactValue("true"); // Simple boolean fact
            fact.setSource("NARRATOR_EMERGENT"); // Source is the narration
            fact.setCreatedBeat(stanza.getCurrentBeat());
            fact.setCreatedExchange(stanza.getCurrentExchange());
            
            // Generate a fact key (simplified - could be smarter)
            String factKey = generateFactKey(transfer.getWhatTheyLearned());
            fact.setFactKey(factKey);
            
            // Add fact to stanza's facts collection
            stanza.getFacts().add(fact);
            
            // 3. Create CharacterKnowledge linking character to fact
            com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge knowledge = 
                new com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge();
            
            knowledge.setCharacter(character);
            knowledge.setFact(fact);
            knowledge.setHow(transfer.getHowLearned()); // OBSERVED, TOLD, INFERRED
            knowledge.setStatus("LEARNED");
            knowledge.setLearnedBeat(stanza.getCurrentBeat());
            knowledge.setLearnedExchange(stanza.getCurrentExchange());
            
            // 4. Add to character's knownFacts collection
            character.getKnownFacts().add(knowledge);
            
            log.debug("[Extraction] Created knowledge: {} learned '{}' via {}", 
                character.getName(), 
                transfer.getWhatTheyLearned(), 
                transfer.getHowLearned());
        }
        
        log.info("[Extraction] Successfully processed {} knowledge transfers", transfers.size());
    }
    
    /**
     * Apply secret revelations to the database.
     * Updates CharacterSecretState entries.
     * 
     * Process:
     * 1. Find the character by name
     * 2. Find the Secret by matching fact predicate to secretDescription
     * 3. Find the CharacterSecretState for that character + secret
     * 4. Update the state (UNAWARE → SUSPICIOUS or KNOWS)
     * 5. Set how it was revealed
     * 
     * NOTE: We're UPDATING existing CharacterSecretState, not creating new ones.
     * CharacterSecretState entries were created at stanza start.
     */
    private void applySecretRevelations(Stanza stanza, java.util.List<SecretRevelation> revelations) {
        if (revelations.isEmpty()) {
            return;
        }
        
        log.info("[Extraction] Applying {} secret revelations", revelations.size());
        
        for (SecretRevelation revelation : revelations) {
            // Validate lengths
            if (revelation.getSecretDescription() != null && revelation.getSecretDescription().length() > 300) {
                log.warn("[Extraction] Secret description exceeds recommended 300 characters: '{}'",
                    revelation.getSecretDescription().substring(0, Math.min(100, revelation.getSecretDescription().length())) + "...");
            }
            if (revelation.getHowRevealed() != null && revelation.getHowRevealed().length() > 200) {
                log.warn("[Extraction] Secret 'howRevealed' exceeds recommended 200 characters");
            }
            
            // 1. Find the character by name
            Optional<com.github.rrousso.erik_core.persistence.entities.StanzaCharacter> charOpt = 
                stanza.getCharacters().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(revelation.getCharacterName()))
                    .findFirst();
            
            if (!charOpt.isPresent()) {
                log.warn("[Extraction] Character '{}' not found - skipping secret revelation", 
                    revelation.getCharacterName());
                continue;
            }
            
            com.github.rrousso.erik_core.persistence.entities.StanzaCharacter character = charOpt.get();
            
            // 2. Find the Secret by matching fact predicate to secretDescription
            // The secret description from Gemini should match (or be similar to) the fact's predicate
            Optional<com.github.rrousso.erik_core.persistence.entities.Secret> secretOpt = 
                stanza.getSecrets().stream()
                    .filter(s -> {
                        String factPredicate = s.getFact().getPredicate();
                        String secretDesc = revelation.getSecretDescription();
                        
                        // Try exact match first (case-insensitive)
                        if (factPredicate.equalsIgnoreCase(secretDesc)) {
                            return true;
                        }
                        
                        // Try partial match (contains)
                        if (factPredicate.toLowerCase().contains(secretDesc.toLowerCase()) ||
                            secretDesc.toLowerCase().contains(factPredicate.toLowerCase())) {
                            return true;
                        }
                        
                        return false;
                    })
                    .findFirst();
            
            if (!secretOpt.isPresent()) {
                log.warn("[Extraction] Secret matching '{}' not found in stanza - skipping", 
                    revelation.getSecretDescription());
                continue;
            }
            
            com.github.rrousso.erik_core.persistence.entities.Secret secret = secretOpt.get();
            
            // 3. Find the CharacterSecretState for this character + secret
            Optional<com.github.rrousso.erik_core.persistence.entities.CharacterSecretState> stateOpt = 
                character.getSecretStates().stream()
                    .filter(css -> css.getSecret().getId().equals(secret.getId()))
                    .findFirst();
            
            if (!stateOpt.isPresent()) {
                log.warn("[Extraction] No CharacterSecretState found for {} and secret '{}' - skipping", 
                    character.getName(), secret.getFact().getPredicate());
                continue;
            }
            
            com.github.rrousso.erik_core.persistence.entities.CharacterSecretState secretState = stateOpt.get();
            
            // 4. Update the state based on newState from extraction
            String oldState = secretState.getState();
            String newState = revelation.getNewState();
            
            if ("KNOWS".equalsIgnoreCase(newState)) {
                // Use the convenience method
                secretState.unlock(
                    revelation.getHowRevealed(), 
                    stanza.getCurrentBeat(), 
                    stanza.getCurrentExchange()
                );
                
                log.info("[Extraction] Secret revealed: {} now KNOWS '{}' (was: {})", 
                    character.getName(), 
                    secret.getFact().getPredicate(), 
                    oldState);
                
            } else if ("SUSPICIOUS".equalsIgnoreCase(newState)) {
                // Use the convenience method
                secretState.makeSuspicious(
                    revelation.getHowRevealed(), 
                    stanza.getCurrentBeat(), 
                    stanza.getCurrentExchange()
                );
                
                log.info("[Extraction] Secret hinted: {} is now SUSPICIOUS about '{}' (was: {})", 
                    character.getName(), 
                    secret.getFact().getPredicate(), 
                    oldState);
                
            } else {
                log.warn("[Extraction] Unknown secret state '{}' - expected KNOWS or SUSPICIOUS", newState);
                continue;
            }
            
            log.debug("[Extraction] Updated secret state for {}: {} → {}", 
                character.getName(), oldState, newState);
        }
        
        log.info("[Extraction] Successfully processed {} secret revelations", revelations.size());
    }
    
    /**
     * Apply tension changes to the database.
     * Updates Tension entries (pressure, status).
     */
    private void applyTensionChanges(Stanza stanza, java.util.List<TensionChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        
        log.info("[Extraction] Applying {} tension changes", changes.size());
        
        for (TensionChange change : changes) {
            // Validate lengths
            if (change.getTensionDescription() != null && change.getTensionDescription().length() > 400) {
                log.warn("[Extraction] Tension description exceeds recommended 400 characters: '{}'",
                    change.getTensionDescription().substring(0, Math.min(100, change.getTensionDescription().length())) + "...");
            }
            if (change.getReason() != null && change.getReason().length() > 200) {
                log.warn("[Extraction] Tension reason exceeds recommended 200 characters");
            }
            if(change.isCreated()) {
            	
                com.github.rrousso.erik_core.persistence.entities.Tension newTension = 
                        new com.github.rrousso.erik_core.persistence.entities.Tension();
                
                newTension.setDescription(change.getTensionDescription());
                newTension.setPressure(change.getNewPressure());
                newTension.setStanza(stanza);
                newTension.setStatus("ACTIVE");
                newTension.setSource("NARRATOR_EMERGENT");  
                newTension.setCreatedBeat(stanza.getCurrentBeat()); 
                newTension.setUpdatedBeat(stanza.getCurrentBeat());
                stanza.getTensions().add(newTension);

            }else {
	            // Find the Tension by matching Tension description to  change tensionDescription
	            // The tension description from Gemini should match (or be similar to) the original tension's predicate
	            Optional<com.github.rrousso.erik_core.persistence.entities.Tension> tensionOpt = 
	                stanza.getTensions().stream()
	                    .filter(t -> {
	                        String tensionDesc = t.getDescription();
	                        String changeTensionDesc = change.getTensionDescription();
	                        
	                        // Try exact match first (case-insensitive)
	                        if (tensionDesc.equalsIgnoreCase(changeTensionDesc)) {
	                            return true;
	                        }
	                        
	                        // Try partial match (contains)
	                        if (tensionDesc.toLowerCase().contains(changeTensionDesc.toLowerCase()) ||
	                        		changeTensionDesc.toLowerCase().contains(tensionDesc.toLowerCase())) {
	                            return true;
	                        }
	                        
	                        return false;
	                    })
	                    .findFirst();
	            
	            if (!tensionOpt.isPresent()) {
	                log.warn("[Extraction] Tension matching '{}' not found in stanza - skipping", 
	                		change.getTensionDescription());
	                continue;
	            }
	            
	            com.github.rrousso.erik_core.persistence.entities.Tension tension = tensionOpt.get();
	            
	            if (change.isResolved()) {
	                tension.resolve(); 
	                
	            } else {
	            	Integer oldPressure = tension.getPressure();
	                Integer newPressure = change.getNewPressure();
	                
	                if (newPressure != null) {
	                    tension.setPressure(newPressure);        
	                    tension.setUpdatedBeat(stanza.getCurrentBeat());
	                    if (newPressure > oldPressure) {
	                        log.info("ESCALATED from {} to {}", oldPressure, newPressure);
	                    } else if (newPressure < oldPressure) {
	                        log.info("DE-ESCALATED from {} to {}", oldPressure, newPressure);
	                    }
	                }
	            }
            }
            log.debug("[Extraction] Tension: {}", change);
        }
    }
    
    /**
     * Apply character appearance changes to the database.
     * Updates StanzaCharacter presence_status.
     * 
     * EMERGENT CHARACTERS:
     * If narrator introduces a character not in the setup, we create them
     * with minimal info flagged as "EMERGENT". Phase 3 can enhance them later.
     */
    private void applyCharacterAppearances(Stanza stanza, java.util.List<CharacterAppearance> appearances) {
        if (appearances.isEmpty()) {
            return;
        }
        
        log.info("[Extraction] Applying {} character appearances", appearances.size());
        
        for (CharacterAppearance appearance : appearances) {
            // Validate lengths
            if (appearance.getCharacterName() != null && appearance.getCharacterName().length() > 100) {
                log.warn("[Extraction] Character name exceeds 100 characters (will be rejected by database)");
                continue;
            }
            if (appearance.getContext() != null && appearance.getContext().length() > 200) {
                log.warn("[Extraction] Appearance context exceeds recommended 200 characters");
            }
            
            // Find character (or create if emergent)
            Optional<com.github.rrousso.erik_core.persistence.entities.StanzaCharacter> charOpt = 
                stanza.getCharacters().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(appearance.getCharacterName()))
                    .findFirst();
            
            com.github.rrousso.erik_core.persistence.entities.StanzaCharacter character;
            
            if (!charOpt.isPresent()) {
                // EMERGENT CHARACTER - narrator invented them
                log.warn("[Extraction] Character '{}' not in setup - creating as EMERGENT", 
                    appearance.getCharacterName());
                
                character = new com.github.rrousso.erik_core.persistence.entities.StanzaCharacter(
                    stanza, 
                    appearance.getCharacterName()
                );
                
                // Flag as emergent with context
                character.setCanonRole("EMERGENT - " + 
                    (appearance.getContext() != null ? appearance.getContext() : "appeared in narration"));
                
                // Minimal setup
                character.setPresenceStatus("present"); // They just appeared
                character.setEmotionalState("Unknown - needs architect setup");
                
                // Add to stanza
                stanza.getCharacters().add(character);
                
                log.info("[Extraction] Created EMERGENT character: {} (needs Phase 3 setup)", 
                    appearance.getCharacterName());
                
            } else {
                character = charOpt.get();
            }
            
            // Handle the appearance change type
            if (appearance.isAppearance()) {
                // Character appeared in scene
                String oldStatus = character.getPresenceStatus();
                character.setPresenceStatus("present");
                
                log.debug("[Extraction] Character '{}' appeared (was: {}, now: present)", 
                    character.getName(), oldStatus);
                
            } else if (appearance.isDeparture()) {
                // Character left scene
                String oldStatus = character.getPresenceStatus();
                character.setPresenceStatus("potential"); // Could return later
                
                log.debug("[Extraction] Character '{}' departed (was: {}, now: potential)", 
                    character.getName(), oldStatus);
                
            } else if (appearance.isMention()) {
                // Character was mentioned but not present
                // Only promote from background to potential if mentioned
                if ("background".equals(character.getPresenceStatus())) {
                    character.setPresenceStatus("potential");
                    log.debug("[Extraction] Character '{}' mentioned (promoted background → potential)", 
                        character.getName());
                }
            }
        }
        
        log.info("[Extraction] Successfully processed {} character appearances", appearances.size());
    }
    
    /**
     * Generate a fact key from a description.
     * Converts to lowercase_snake_case and truncates to 50 chars.
     */
    private String generateFactKey(String description) {
        if (description == null || description.isEmpty()) {
            return "unknown_fact";
        }
        
        // Convert to lowercase, replace spaces/punctuation with underscore
        String key = description.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", ""); // Remove leading/trailing underscores
        
        // Truncate to 50 characters
        if (key.length() > 50) {
            key = key.substring(0, 50);
            // Remove trailing underscore if truncation created one
            key = key.replaceAll("_+$", "");
        }
        
        return key;
    }
}