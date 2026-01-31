package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.KnowledgeTransfer;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

/**
 * Applier for knowledge transfer extractions.
 * 
 * Creates Fact entries and CharacterKnowledge links when characters learn information.
 * 
 * Process:
 * 1. Validate description length (recommended max 200 chars)
 * 2. Find the character who learned the knowledge
 * 3. Create a new Fact entity (the knowledge itself)
 * 4. Generate a unique fact key for indexing
 * 5. Create a CharacterKnowledge link (character ↔ fact)
 * 6. Add both to the stanza's collections (cascade will persist them)
 * 
 * Knowledge transfers represent information characters learn during exchanges:
 * - "Derek learned that the supernatural world exists" (via OBSERVED)
 * - "Stiles learned that Scott is a werewolf" (via TOLD)
 * - "Allison inferred that her family are hunters" (via INFERRED)
 */
@Component
public class KnowledgeTransferApplier implements ExtractionApplier<KnowledgeTransfer> {
    
    private static final Logger log = LoggerFactory.getLogger(KnowledgeTransferApplier.class);
    
    private static final int MAX_DESCRIPTION_LENGTH = 200;
    private static final int MAX_KEY_LENGTH = 50;
    
    @Override
    public void apply(Stanza stanza, KnowledgeTransfer transfer) {
        // Validate description length (soft warning - we don't truncate facts)
        if (transfer.getWhatTheyLearned() != null && transfer.getWhatTheyLearned().length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[KnowledgeTransferApplier] Knowledge description exceeds recommended {} characters: '{}'",
                MAX_DESCRIPTION_LENGTH,
                transfer.getWhatTheyLearned().substring(0, Math.min(100, transfer.getWhatTheyLearned().length())) + "...");
        }
        
        // 1. Find the character by name (case-insensitive)
        Optional<StanzaCharacter> charOpt = 
            stanza.getCharacters().stream()
                .filter(c -> c.getName().equalsIgnoreCase(transfer.getCharacterName()))
                .findFirst();
        
        if (!charOpt.isPresent()) {
            log.warn("[KnowledgeTransferApplier] Character '{}' not found in stanza - skipping knowledge transfer", 
                transfer.getCharacterName());
            return;
        }
        
        StanzaCharacter character = charOpt.get();
        
        // 2. Create the Fact entity (the knowledge itself)
        Fact fact = new Fact();
        
        fact.setStanza(stanza);
        fact.setKind("OBSERVED"); // Default kind - could be enhanced based on howLearned
        fact.setPredicate(transfer.getWhatTheyLearned());
        fact.setFactValue("true"); // Simple boolean fact
        fact.setSource("NARRATOR_EMERGENT"); // Source is the narration
        fact.setCreatedBeat(stanza.getCurrentBeat());
        fact.setCreatedExchange(stanza.getCurrentExchange());
        
        // Generate a fact key (for indexing/lookup)
        String factKey = generateFactKey(transfer.getWhatTheyLearned());
        fact.setFactKey(factKey);
        
        // Add fact to stanza's facts collection
        stanza.getFacts().add(fact);
        
        // 3. Create CharacterKnowledge linking character to fact
        CharacterKnowledge knowledge = new CharacterKnowledge();
        
        knowledge.setCharacter(character);
        knowledge.setFact(fact);
        knowledge.setHow(transfer.getHowLearned()); // OBSERVED, TOLD, INFERRED
        knowledge.setStatus("LEARNED");
        knowledge.setLearnedBeat(stanza.getCurrentBeat());
        knowledge.setLearnedExchange(stanza.getCurrentExchange());
        
        // 4. Add to character's knownFacts collection
        character.getKnownFacts().add(knowledge);
        
        log.debug("[KnowledgeTransferApplier] Created knowledge: {} learned '{}' via {}", 
            character.getName(), 
            transfer.getWhatTheyLearned(), 
            transfer.getHowLearned());
    }
    
    @Override
    public String getTypeName() {
        return "KnowledgeTransfer";
    }
    
    /**
     * Generate a fact key from a description.
     * Converts to lowercase_snake_case and truncates to 50 chars.
     * 
     * Examples:
     * - "Supernatural world exists" → "supernatural_world_exists"
     * - "He lost Alan's phone charger" → "he_lost_alan_s_phone_charger"
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
        if (key.length() > MAX_KEY_LENGTH) {
            key = key.substring(0, MAX_KEY_LENGTH);
            // Remove trailing underscore if truncation created one
            key = key.replaceAll("_+$", "");
        }
        
        return key;
    }
}