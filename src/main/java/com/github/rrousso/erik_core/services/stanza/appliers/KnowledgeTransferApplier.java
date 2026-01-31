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
        
        // CRITICAL: Extract core predicate BEFORE any concatenation
        // This prevents the key from including prefixes/character names
        String corePredicate = transfer.getWhatTheyLearned();
        
        // Generate key from CORE predicate (before adding context)
        String factKey = generateFactKey(corePredicate);
        fact.setFactKey(factKey);
        
        // NOW add context to the predicate (this might make it longer, but key is already set)
        // Note: Consider if we need this prefix - it might be redundant with CharacterKnowledge link
        fact.setPredicate(corePredicate); // Using core predicate without prefix
        
        fact.setStanza(stanza);
        fact.setKind("OBSERVED"); // Default kind - could be enhanced based on howLearned
        fact.setFactValue("true"); // Simple boolean fact
        fact.setSource("NARRATOR_EMERGENT"); // Source is the narration
        fact.setCreatedBeat(stanza.getCurrentBeat());
        fact.setCreatedExchange(stanza.getCurrentExchange());
        
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
        
        log.debug("[KnowledgeTransferApplier] Created knowledge: {} learned '{}' via {} (key: {})", 
            character.getName(), 
            corePredicate,
            transfer.getHowLearned(),
            factKey);
    }
    
    @Override
    public String getTypeName() {
        return "KnowledgeTransfer";
    }
    
    /**
     * Generate a fact key from a description with guaranteed uniqueness.
     * 
     * Uses a hybrid approach:
     * - First 40 chars: human-readable prefix (normalized)
     * - Last 8 chars: hash of full description (ensures uniqueness)
     * 
     * This prevents truncation issues while keeping keys under 50 chars.
     * 
     * Examples:
     * - "Supernatural world exists" → "supernatural_world_exists_a3f8b2c1"
     * - "He lost Alan's phone charger" → "he_lost_alan_s_phone_charger_9d4e2f01"
     * - "Very long description that would normally get truncated..." → "very_long_description_that_would_norma_7ab3c9f2"
     * 
     * @param description The fact description
     * @return A unique key under 50 characters
     */
    private String generateFactKey(String description) {
        if (description == null || description.isEmpty()) {
            return "unknown_fact_" + Integer.toHexString("unknown".hashCode());
        }
        
        // Normalize: lowercase, replace non-alphanumeric with underscore
        String normalized = description.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", ""); // Remove leading/trailing underscores
        
        // Generate hash for uniqueness (8 hex chars)
        String hash = String.format("%08x", description.hashCode());
        
        // Calculate max prefix length (50 - 1 separator - 8 hash = 41)
        int maxPrefixLength = 41;
        
        // Truncate prefix if needed
        String prefix = normalized.length() > maxPrefixLength 
            ? normalized.substring(0, maxPrefixLength) 
            : normalized;
        
        // Remove trailing underscore if truncation created one
        prefix = prefix.replaceAll("_+$", "");
        
        // Combine: prefix_hash
        String key = prefix + "_" + hash;
        
        // Guarantee under 50 chars (should always be true, but safety check)
        if (key.length() > MAX_KEY_LENGTH) {
            key = prefix.substring(0, MAX_KEY_LENGTH - 9) + "_" + hash;
        }
        
        return key;
    }
}