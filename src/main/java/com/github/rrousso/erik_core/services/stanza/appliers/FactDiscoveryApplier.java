package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.FactDiscovery;
import com.github.rrousso.erik_core.dto.extraction.FactDiscovery.CharacterDiscovery;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.util.FactUtility;

/**
 * Applier for fact discovery extractions.
 * 
 * This combines two operations that naturally occur together:
 * 1. Fact establishment - Creating a new fact in the world
 * 2. Knowledge transfer - Characters learning about that fact
 * 
 * Process:
 * 1. Determine if this is a new fact or reference to existing fact
 * 2. Create new Fact entity if needed, or look up existing one
 * 3. For each character in discoveredBy list:
 *    - Find the character entity
 *    - Create CharacterKnowledge linking character to fact
 *    - Set how they learned it
 * 
 * This mirrors natural storytelling: when Derek opens a box, both he and
 * anyone watching learn what's inside simultaneously.
 */
@Component
public class FactDiscoveryApplier implements ExtractionApplier<FactDiscovery> {
    
    private static final Logger log = LoggerFactory.getLogger(FactDiscoveryApplier.class);
    
    @Override
    public void apply(Stanza stanza, FactDiscovery discovery) {
        // Validate
        if (!discovery.isValid()) {
            log.warn("[FactDiscoveryApplier] Invalid discovery - missing required fields");
            return;
        }
        
        Fact fact;
        
        // Step 1: Get or create the fact
        if (discovery.isNewFact()) {
            fact = createNewFact(stanza, discovery);
            if (fact == null) {
                return; // Creation failed or duplicate
            }
        } else {
            fact = findExistingFact(stanza, discovery.getExistingFactHash());
            if (fact == null) {
                log.warn("[FactDiscoveryApplier] Existing fact with hash '{}' not found - skipping",
                    discovery.getExistingFactHash());
                return;
            }
        }
        
        // Step 2: Link all characters who discovered this fact
        int successfulLinks = 0;
        for (CharacterDiscovery charDiscovery : discovery.getDiscoveredBy()) {
            if (linkCharacterToFact(stanza, charDiscovery, fact)) {
                successfulLinks++;
            }
        }
        
        log.info("[FactDiscoveryApplier] Processed discovery: {} learned by {}/{} characters",
            discovery.isNewFact() ? "NEW fact" : "EXISTING fact [" + discovery.getExistingFactHash() + "]",
            successfulLinks,
            discovery.getDiscoveredBy().size());
    }
    
    /**
     * Create a new fact from discovery data
     */
    private Fact createNewFact(Stanza stanza, FactDiscovery discovery) {
        String factKey = FactUtility.generateFactKey(discovery.getStatement());
        
        // Check if fact already exists (avoid duplicates)
        boolean exists = stanza.getFacts().stream()
            .anyMatch(f -> f.getFactKey().equals(factKey));
            
        if (exists) {
            log.debug("[FactDiscoveryApplier] Fact already exists: {}", factKey);
            // Still return it so we can link characters
            return stanza.getFacts().stream()
                .filter(f -> f.getFactKey().equals(factKey))
                .findFirst()
                .orElse(null);
        }
        
        // Create new fact
        Fact fact = new Fact();
        fact.setStanza(stanza);
        fact.setFactKey(factKey);
        fact.setPredicate(FactUtility.truncatePredicate(
            discovery.getStatement(), 
            "FactDiscovery"
        ));
        fact.setFactValue(discovery.getTruthValue() != null 
            ? discovery.getTruthValue().toString() 
            : "true");
        fact.setKind("EMERGENT");
        fact.setSource("NARRATOR_EMERGENT");
        fact.setCreatedBeat(stanza.getCurrentBeatNumber());
        fact.setCreatedExchange(stanza.getCurrentExchange());
        
        // Set discovery rules if restricted
        if (discovery.isRestricted()) {
            fact.setAllowedRevealModes(discovery.getAllowedRevealModes());
        }
        
        // Add to stanza
        stanza.getFacts().add(fact);
        
        String hash = FactUtility.extractHash(factKey);
        log.info("[FactDiscoveryApplier] Created new fact [{}]: {} (restricted: {})", 
            hash,
            discovery.getStatement(),
            discovery.isRestricted());
        
        return fact;
    }
    
    /**
     * Find an existing fact by hash
     */
    private Fact findExistingFact(Stanza stanza, String hash) {
        return stanza.getFacts().stream()
            .filter(f -> FactUtility.matchesHash(f.getFactKey(), hash))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Link a character to a fact they discovered
     * 
     * @return true if link was created, false if skipped
     */
    private boolean linkCharacterToFact(Stanza stanza, CharacterDiscovery charDiscovery, Fact fact) {
        // Find character
    	Optional<StanzaCharacter> charOpt = stanza.findCharacterByName(charDiscovery.getCharacterName());
        
        if (!charOpt.isPresent()) {
            log.warn("[FactDiscoveryApplier] Character '{}' not found - skipping knowledge link",
                charDiscovery.getCharacterName());
            return false;
        }
        
        StanzaCharacter character = charOpt.get();
        
        // Check if character already knows this fact
        String factHash = FactUtility.extractHash(fact.getFactKey());
        boolean alreadyKnows = character.getKnownFacts().stream()
            .anyMatch(ck -> {
                String knownFactHash = FactUtility.extractHash(ck.getFact().getFactKey());
                return factHash != null && factHash.equals(knownFactHash);
            });
        
        if (alreadyKnows) {
            log.debug("[FactDiscoveryApplier] Character '{}' already knows fact [{}] - skipping",
                character.getName(), factHash);
            return false;
        }
        
        // Create knowledge link
        CharacterKnowledge knowledge = new CharacterKnowledge();
        knowledge.setCharacter(character);
        knowledge.setFact(fact);
        knowledge.setAwarenessState("KNOWS");
        knowledge.setHow(charDiscovery.getHowLearned());
        knowledge.setStatus("LEARNED");
        knowledge.setLearnedBeat(stanza.getCurrentBeatNumber());
        knowledge.setLearnedExchange(stanza.getCurrentExchange());
        
        character.getKnownFacts().add(knowledge);
        
        log.debug("[FactDiscoveryApplier] {} learned '{}' via {}",
            character.getName(),
            fact.getPredicate(),
            charDiscovery.getHowLearned());
        
        return true;
    }
    
    @Override
    public String getTypeName() {
        return "FactDiscovery";
    }
}