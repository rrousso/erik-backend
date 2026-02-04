package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.CharacterAppearance;
import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.dto.extraction.FactEstablishment;
import com.github.rrousso.erik_core.dto.extraction.KnowledgeTransfer;
import com.github.rrousso.erik_core.dto.extraction.SecretRevelation;
import com.github.rrousso.erik_core.dto.extraction.TensionChange;
import com.github.rrousso.erik_core.persistence.entities.Stanza;

/**
 * Registry for extraction appliers.
 * 
 * This is NOT a traditional factory because we're not selecting ONE applier.
 * Instead, this is a REGISTRY that provides convenient methods to apply
 * MULTIPLE extractions of each type.
 * 
 * The pattern here is:
 * 1. Dependency Injection - Spring injects all applier instances
 * 2. Type-safe delegation - Each method knows its extraction type
 * 3. Iterator pattern - We loop through lists and apply each item
 * 
 * Why this approach instead of a Map?
 * - Type safety: Compiler ensures we pass the right extraction type
 * - Clarity: Method names are self-documenting
 * - No reflection: Direct method calls are faster and safer
 * 
 * Example usage:
 * ```
 * applierRegistry.applyEvents(stanza, extractionResult.getEvents());
 * applierRegistry.applyKnowledgeTransfers(stanza, extractionResult.getKnowledgeTransfers());
 * ```
 */
@Component
public class ExtractionApplierRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(ExtractionApplierRegistry.class);
    
    // Individual appliers injected by Spring
    private final EventApplier eventApplier;
    private final FactEstablishmentApplier factEstablishmentApplier;
    private final KnowledgeTransferApplier knowledgeTransferApplier;
    private final SecretRevelationApplier secretRevelationApplier;
    private final TensionChangeApplier tensionChangeApplier;
    private final CharacterAppearanceApplier characterAppearanceApplier;
    
    public ExtractionApplierRegistry(
            EventApplier eventApplier,
            FactEstablishmentApplier factEstablishmentApplier,
            KnowledgeTransferApplier knowledgeTransferApplier,
            SecretRevelationApplier secretRevelationApplier,
            TensionChangeApplier tensionChangeApplier,
            CharacterAppearanceApplier characterAppearanceApplier) {
        this.eventApplier = eventApplier;
        this.factEstablishmentApplier = factEstablishmentApplier;
        this.knowledgeTransferApplier = knowledgeTransferApplier;
        this.secretRevelationApplier = secretRevelationApplier;
        this.tensionChangeApplier = tensionChangeApplier;
        this.characterAppearanceApplier = characterAppearanceApplier;
        
        log.info("ExtractionApplierRegistry initialized with {} applier types", 6);
    }
    
    /**
     * Apply a list of event extractions.
     * 
     * This demonstrates the key pattern:
     * 1. Check if list is empty (early return)
     * 2. Log how many we're applying
     * 3. Loop through list
     * 4. Apply each one using the appropriate applier
     * 5. Log completion
     */
    public void applyEvents(Stanza stanza, List<EventExtraction> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} events", events.size());
        
        for (EventExtraction event : events) {
            eventApplier.apply(stanza, event);
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} events", events.size());
    }
    
    /**
     * Apply a list of fact establishments.
     * 
     * Creates new facts in the world that exist but may not be known by characters yet.
     */
    public void applyFactEstablishments(Stanza stanza, List<FactEstablishment> establishments) {
        if (establishments == null || establishments.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} fact establishments", establishments.size());
        
        for (FactEstablishment establishment : establishments) {
            try {
                factEstablishmentApplier.apply(stanza, establishment);
            } catch (Exception e) {
                log.error("[ExtractionApplierRegistry] Failed to apply fact establishment: {}", 
                    establishment.getStatement(), e);
                // Continue with remaining establishments
            }
        }
    }
    
    /**
     * Apply a list of knowledge transfer extractions.
     */
    public void applyKnowledgeTransfers(Stanza stanza, List<KnowledgeTransfer> transfers) {
        if (transfers == null || transfers.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} knowledge transfers", transfers.size());
        
        for (KnowledgeTransfer transfer : transfers) {
            knowledgeTransferApplier.apply(stanza, transfer);
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} knowledge transfers", transfers.size());
    }
    
    /**
     * Apply a list of secret revelation extractions.
     */
    public void applySecretRevelations(Stanza stanza, List<SecretRevelation> revelations) {
        if (revelations == null || revelations.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} secret revelations", revelations.size());
        
        for (SecretRevelation revelation : revelations) {
            try {
                secretRevelationApplier.apply(stanza, revelation);
            } catch (Exception e) {
                log.error("[ExtractionApplierRegistry] Failed to apply secret revelation for character '{}': {}", 
                    revelation.getCharacterName(), e.getMessage(), e);
                // Continue with remaining revelations
            }
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} secret revelations", revelations.size());
    }
    
    /**
     * Apply a list of tension change extractions.
     */
    public void applyTensionChanges(Stanza stanza, List<TensionChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} tension changes", changes.size());
        
        for (TensionChange change : changes) {
            tensionChangeApplier.apply(stanza, change);
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} tension changes", changes.size());
    }
    
    /**
     * Apply a list of character appearance extractions.
     */
    public void applyCharacterAppearances(Stanza stanza, List<CharacterAppearance> appearances) {
        if (appearances == null || appearances.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} character appearances", appearances.size());
        
        for (CharacterAppearance appearance : appearances) {
            characterAppearanceApplier.apply(stanza, appearance);
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} character appearances", appearances.size());
    }
}