package com.github.rrousso.erik_core.services.stanza.appliers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.FactEstablishment;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.util.FactUtility;

/**
 * Applier for fact establishment extractions.
 * 
 * Creates new facts that exist in the world but aren't yet known by characters.
 * These facts can be discovered later through KnowledgeTransfer.
 * 
 * Process:
 * 1. Generate fact key from statement
 * 2. Check if fact already exists (avoid duplicates)
 * 3. Create new Fact entity with predicate = statement, factValue = truthValue
 * 4. Set allowedRevealModes if the fact is restricted
 * 5. Add to stanza's facts collection
 * 
 * Example: User creates a locked box containing gold. The fact exists,
 * but characters only learn it when they open the box and trigger a KnowledgeTransfer.
 */
@Component
public class FactEstablishmentApplier implements ExtractionApplier<FactEstablishment> {
    
    private static final Logger log = LoggerFactory.getLogger(FactEstablishmentApplier.class);
    
    @Override
    public void apply(Stanza stanza, FactEstablishment establishment) {
        // Validate input
        if (establishment.getStatement() == null || establishment.getStatement().isEmpty()) {
            log.warn("[FactEstablishmentApplier] Empty statement - skipping");
            return;
        }
        
        // Generate fact key
        String factKey = FactUtility.generateFactKey(establishment.getStatement());
        
        // Check if fact already exists
        boolean exists = stanza.getFacts().stream()
            .anyMatch(f -> f.getFactKey().equals(factKey));
            
        if (exists) {
            log.debug("[FactEstablishmentApplier] Fact already exists: {}", factKey);
            return;
        }
        
        // Create new fact
        Fact fact = new Fact();
        fact.setStanza(stanza);
        fact.setFactKey(factKey);
        fact.setPredicate(FactUtility.truncatePredicate(
            establishment.getStatement(), 
            "FactEstablishment"
        ));
        fact.setFactValue(establishment.getTruthValue() != null 
            ? establishment.getTruthValue().toString() 
            : "true");
        fact.setKind("EMERGENT"); // Facts that emerged during play
        fact.setSource("NARRATOR_EMERGENT");
        fact.setCreatedBeat(stanza.getCurrentBeatNumber());
        fact.setCreatedExchange(stanza.getCurrentExchange());
        
        // Set discovery rules if specified
        if (establishment.getAllowedRevealModes() != null 
            && !establishment.getAllowedRevealModes().isEmpty()) {
            fact.setAllowedRevealModes(establishment.getAllowedRevealModes());
        }
        
        // Add to stanza
        stanza.getFacts().add(fact);
        
        String hash = FactUtility.extractHash(factKey);
        log.info("[FactEstablishmentApplier] Created new fact [{}]: {} (restricted: {})", 
            hash,
            establishment.getStatement(),
            fact.isRestricted());
    }
    
    @Override
    public String getTypeName() {
        return "FactEstablishment";
    }
}