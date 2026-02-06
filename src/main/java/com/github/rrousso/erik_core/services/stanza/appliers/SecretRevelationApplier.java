package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.SecretRevelation;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.util.FactUtility;

/**
 * Applier for secret revelation extractions.
 * 
 * Updates CharacterKnowledge awareness state when characters learn or suspect restricted facts.
 * 
 * NEW APPROACH (post-refactor):
 * - No more Secret entity
 * - No more CharacterSecretState entity
 * - CharacterKnowledge.awarenessState tracks KNOWS/SUSPICIOUS
 * - If no CharacterKnowledge exists, character is UNAWARE (implicit)
 * 
 * Process:
 * 1. Find the character
 * 2. Find the fact (by hash or description matching)
 * 3. Find or create CharacterKnowledge record
 * 4. Update awarenessState to KNOWS or SUSPICIOUS
 */
@Component
public class SecretRevelationApplier implements ExtractionApplier<SecretRevelation> {
    
    private static final Logger log = LoggerFactory.getLogger(SecretRevelationApplier.class);
    
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_HOWREVEALED_LENGTH = 200;

    @Override
    public void apply(Stanza stanza, SecretRevelation revelation) {
        // Validate lengths
        if (revelation.getSecretDescription() != null && revelation.getSecretDescription().length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[SecretRevelationApplier] Secret description exceeds recommended {} characters",
                MAX_DESCRIPTION_LENGTH);
        }
        if (revelation.getHowRevealed() != null && revelation.getHowRevealed().length() > MAX_HOWREVEALED_LENGTH) {
            log.warn("[SecretRevelationApplier] 'howRevealed' exceeds recommended {} characters", 
                MAX_HOWREVEALED_LENGTH);
        }
        
        // 1. Find the character
        Optional<StanzaCharacter> charOpt = stanza.findCharacterByName(revelation.getCharacterName());
        
        if (!charOpt.isPresent()) {
            log.warn("[SecretRevelationApplier] Character '{}' not found - skipping secret revelation", 
                revelation.getCharacterName());
            return;
        }
        
        StanzaCharacter character = charOpt.get();
        
        // 2. Find the fact
        final Fact fact;
        
        // Try to find by hash first (preferred)
        if (revelation.isHashReference()) {
            Fact foundFact = stanza.getFacts().stream()
                .filter(f -> FactUtility.matchesHash(f.getFactKey(), revelation.getSecretHash()))
                .findFirst()
                .orElse(null);
                
            if (foundFact == null) {
                log.warn("[SecretRevelationApplier] Fact with hash '{}' not found - skipping", 
                    revelation.getSecretHash());
                return;
            }
            fact = foundFact;
        } else {
            // Fall back to matching by description (less reliable)
            String secretDesc = revelation.getSecretDescription();
            Fact foundFact = stanza.getFacts().stream()
                .filter(f -> {
                    String predicate = f.getPredicate();
                    // Try exact match
                    if (predicate.equalsIgnoreCase(secretDesc)) {
                        return true;
                    }
                    // Try partial match
                    return predicate.toLowerCase().contains(secretDesc.toLowerCase()) ||
                           secretDesc.toLowerCase().contains(predicate.toLowerCase());
                })
                .findFirst()
                .orElse(null);
                
            if (foundFact == null) {
                log.warn("[SecretRevelationApplier] Fact matching '{}' not found - skipping", 
                    secretDesc);
                return;
            }
            fact = foundFact;
        }
        
        // 3. Find or create CharacterKnowledge record
        // Compare by hash (not ID) since new facts don't have IDs yet
        final String factHash = FactUtility.extractHash(fact.getFactKey());
        CharacterKnowledge knowledge = character.getKnownFacts().stream()
            .filter(k -> {
                String knownFactHash = FactUtility.extractHash(k.getFact().getFactKey());
                return factHash != null && factHash.equals(knownFactHash);
            })
            .findFirst()
            .orElse(null);
        
        String oldState = knowledge != null ? knowledge.getAwarenessState() : "UNAWARE";
        
        if (knowledge == null) {
            // Character didn't have a knowledge record - create one
            knowledge = new CharacterKnowledge();
            knowledge.setCharacter(character);
            knowledge.setFact(fact);
            knowledge.setStatus("LEARNED");
            character.getKnownFacts().add(knowledge);
        }
        
        // 4. Update awareness state
        String newState = revelation.getNewState();
        
        if ("KNOWS".equalsIgnoreCase(newState)) {
            knowledge.unlock(
                revelation.getHowRevealed(), 
                stanza.getCurrentBeatNumber(), 
                stanza.getCurrentExchange()
            );
            
            log.info("[SecretRevelationApplier] Secret revealed: {} now KNOWS '{}' (was: {})", 
                character.getName(), 
                fact.getPredicate(), 
                oldState);
            
        } else if ("SUSPICIOUS".equalsIgnoreCase(newState)) {
            knowledge.makeSuspicious(
                revelation.getHowRevealed(), 
                stanza.getCurrentBeatNumber(), 
                stanza.getCurrentExchange()
            );
            
            log.info("[SecretRevelationApplier] Secret hinted: {} is now SUSPICIOUS about '{}' (was: {})", 
                character.getName(), 
                fact.getPredicate(), 
                oldState);
            
        } else {
            log.warn("[SecretRevelationApplier] Unknown state '{}' - expected KNOWS or SUSPICIOUS", newState);
            return;
        }
        
        log.debug("[SecretRevelationApplier] Updated awareness for {}: {} → {}", 
            character.getName(), oldState, newState);
    }
    
    @Override
    public String getTypeName() {
        return "SecretRevelation";
    }
}