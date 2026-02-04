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
import com.github.rrousso.erik_core.util.FactUtility;

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
    
    private static final int MAX_LEARNED_LENGTH = 200;
    
    @Override
    public void apply(Stanza stanza, KnowledgeTransfer transfer) {
        // Validate input
        if (transfer.getWhatTheyLearned() != null && transfer.getWhatTheyLearned().length() > MAX_LEARNED_LENGTH) {
            log.warn("[KnowledgeTransferApplier] 'whatTheyLearned' exceeds recommended {} characters", 
                MAX_LEARNED_LENGTH);
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
        Fact fact;
        
        // 2. Determine if this is a reference to existing fact or a new fact
        if (transfer.isExistingFactReference()) {
            // CASE A: Character learned an EXISTING fact
            fact = findFactByHash(stanza, transfer.getExistingFactHash());
            
            if (fact == null) {
                log.warn("[KnowledgeTransferApplier] Fact with hash '{}' not found - skipping", 
                    transfer.getExistingFactHash());
                return;
            }
            
            log.debug("[KnowledgeTransferApplier] Character '{}' learned existing fact [{}]: {}", 
                character.getName(), 
                transfer.getExistingFactHash(),
                fact.getPredicate());
            
        } else if (transfer.isNewFact()) {
            // CASE B: Character learned NEW emergent fact
            fact = new Fact();
            
            String corePredicate = transfer.getWhatTheyLearned();
            String factKey = FactUtility.generateFactKey(corePredicate);
            
            fact.setFactKey(factKey);
            fact.setPredicate(FactUtility.truncatePredicate(corePredicate, "KnowledgeTransfer from " + character.getName()));
            fact.setStanza(stanza);
            fact.setKind("OBSERVED"); // Emerged from narration
            fact.setFactValue("true");
            fact.setSource("NARRATOR_EMERGENT");
            fact.setCreatedBeat(stanza.getCurrentBeatNumber());
            fact.setCreatedExchange(stanza.getCurrentExchange());
            
            // Add to stanza's facts collection
            stanza.getFacts().add(fact);
            
            String hash = FactUtility.extractHash(factKey);
            log.debug("[KnowledgeTransferApplier] Created NEW fact [{}]: {}", 
                hash,
                corePredicate);
            
        } else {
            log.warn("[KnowledgeTransferApplier] KnowledgeTransfer has neither whatTheyLearned nor existingFactHash - skipping");
            return;
        }
        
        // 3. Check if character already knows this fact
        // Compare by hash (not ID) since:
        // - New facts don't have IDs yet (JPA assigns on persist)
        // - Existing facts are referenced by hash in the extraction system
        // - Hash is the canonical identifier Gemini uses
        String factHash = FactUtility.extractHash(fact.getFactKey());
        boolean alreadyKnows = character.getKnownFacts().stream()
            .anyMatch(ck -> {
                String knownFactHash = FactUtility.extractHash(ck.getFact().getFactKey());
                return factHash != null && factHash.equals(knownFactHash);
            });
        
        if (alreadyKnows) {
            log.debug("[KnowledgeTransferApplier] Character '{}' already knows fact [{}] - skipping duplicate", 
                character.getName(),
                FactUtility.extractHash(fact.getFactKey()));
            return;
        }
        
        // 4. Create CharacterKnowledge linking character to fact
        CharacterKnowledge knowledge = new CharacterKnowledge();
        knowledge.setCharacter(character);
        knowledge.setFact(fact);
        knowledge.setHow(transfer.getHowLearned());
        knowledge.setStatus("LEARNED");
        knowledge.setLearnedBeat(stanza.getCurrentBeatNumber());
        knowledge.setLearnedExchange(stanza.getCurrentExchange());
        
        // 5. Add to character's knownFacts collection
        character.getKnownFacts().add(knowledge);
        
        log.debug("[KnowledgeTransferApplier] {} learned '{}' via {}", 
            character.getName(), 
            fact.getPredicate(),
            transfer.getHowLearned());
    }

    /**
     * Find a fact by its hash suffix
     */
    private Fact findFactByHash(Stanza stanza, String hash) {
        return stanza.getFacts().stream()
            .filter(f -> FactUtility.matchesHash(f.getFactKey(), hash))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public String getTypeName() {
        return "KnowledgeTransfer";
    }
    
}