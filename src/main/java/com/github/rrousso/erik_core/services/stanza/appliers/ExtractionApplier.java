package com.github.rrousso.erik_core.services.stanza.appliers;

import com.github.rrousso.erik_core.persistence.entities.Stanza;

/**
 * Strategy interface for applying specific types of extracted changes to the database.
 * 
 * Each implementation handles one category of extraction:
 * - EventApplier: Creates StanzaEvent entries
 * - KnowledgeTransferApplier: Creates CharacterKnowledge entries
 * - SecretRevelationApplier: Updates Secret exposure/suspicion
 * - TensionChangeApplier: Updates Tension pressure/resolution
 * - CharacterAppearanceApplier: Updates StanzaCharacter presence_status
 * 
 * This pattern allows us to:
 * - Test each applier independently
 * - Add new extraction types without modifying existing code
 * - Keep each applier focused on a single responsibility
 * 
 * @param <T> The type of extraction data this applier handles
 *           (e.g., EventExtraction, KnowledgeTransfer, etc.)
 */
public interface ExtractionApplier<T> {
    
    /**
     * Apply a single extracted change to the stanza.
     * 
     * This method should:
     * 1. Find the relevant entity in the stanza (if needed)
     * 2. Create new entities or update existing ones
     * 3. Add/update relationships
     * 4. Log what was done
     * 
     * Note: The stanza parameter is the loaded entity with all relationships.
     * Changes made here will be persisted when the transaction commits.
     * 
     * @param stanza The stanza entity being updated (loaded with relationships)
     * @param extraction The extracted data to apply
     */
    void apply(Stanza stanza, T extraction);
    
    /**
     * Get the type name for logging purposes.
     * 
     * Examples: "Event", "KnowledgeTransfer", "SecretRevelation"
     * 
     * @return A human-readable name for this extraction type
     */
    default String getTypeName() {
        return this.getClass().getSimpleName().replace("Applier", "");
    }
}