package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.BlueprintUpdate;
import com.github.rrousso.erik_core.dto.extraction.CharacterAppearance;
import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.dto.extraction.FactDiscovery;
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
    private final FactDiscoveryApplier factDiscoveryApplier;
    private final SecretRevelationApplier secretRevelationApplier;
    private final TensionChangeApplier tensionChangeApplier;
    private final CharacterAppearanceApplier characterAppearanceApplier;
    private final BlueprintUpdateApplier blueprintUpdateApplier;
    
    public ExtractionApplierRegistry(
            EventApplier eventApplier,
            FactDiscoveryApplier factDiscoveryApplier,  
            SecretRevelationApplier secretRevelationApplier,
            TensionChangeApplier tensionChangeApplier,
            CharacterAppearanceApplier characterAppearanceApplier,
            BlueprintUpdateApplier blueprintUpdateApplier) {
        this.eventApplier = eventApplier;
        this.factDiscoveryApplier = factDiscoveryApplier; 
        this.secretRevelationApplier = secretRevelationApplier;
        this.tensionChangeApplier = tensionChangeApplier;
        this.characterAppearanceApplier = characterAppearanceApplier;
		this.blueprintUpdateApplier = blueprintUpdateApplier;
        
        log.info("ExtractionApplierRegistry initialized with {} applier types", 7);
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
     * Apply a list of fact discoveries.
     * 
     * Each discovery combines fact creation with immediate knowledge transfer.
     * This is the primary way facts should be created during extraction.
     */
    public void applyFactDiscoveries(Stanza stanza, List<FactDiscovery> discoveries) {
        if (discoveries == null || discoveries.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} fact discoveries", discoveries.size());
        
        for (FactDiscovery discovery : discoveries) {
            try {
                factDiscoveryApplier.apply(stanza, discovery);
            } catch (Exception e) {
                log.error("[ExtractionApplierRegistry] Failed to apply fact discovery: {}", 
                    discovery, e);
                // Continue with remaining discoveries
            }
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} fact discoveries", discoveries.size());
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
    
    /**
     * Apply a list of blueprint/visual appearance updates.
     */
    public void applyBlueprintUpdates(Stanza stanza, List<BlueprintUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        
        log.info("[ExtractionApplierRegistry] Applying {} blueprint updates", updates.size());
        
        for (BlueprintUpdate update : updates) {
            blueprintUpdateApplier.apply(stanza, update);
        }
        
        log.debug("[ExtractionApplierRegistry] Successfully applied {} blueprint updates", updates.size());
    }
}