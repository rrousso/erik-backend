package com.github.rrousso.erik_core.dto.extraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Main DTO for extraction results from the analytical LLM.
 * 
 * This maps to the top-level JSON structure returned by Gemini
 * when analyzing a narrative exchange for state changes.
 * 
 * Each field corresponds to a category of changes that can occur
 * during an exchange:
 * - Events: Things that happened
 * - Knowledge transfers: Characters learning information
 * - Secret revelations: Secrets being exposed or suspected
 * - Tension changes: Story threads escalating/resolving
 * - Character appearances: Characters entering/leaving scene
 */
public class ExtractionResult {
    
    private List<EventExtraction> events = new ArrayList<>();
    private List<FactEstablishment> facts = new ArrayList<>();
    private List<KnowledgeTransfer> knowledgeTransfers = new ArrayList<>();
    private List<SecretRevelation> secretRevelations = new ArrayList<>();
    private List<TensionChange> tensionChanges = new ArrayList<>();
    private List<CharacterAppearance> characterAppearances = new ArrayList<>();
    
    // === CONSTRUCTORS ===
    
    public ExtractionResult() {}
    
    // === GETTERS AND SETTERS ===
    
    public List<EventExtraction> getEvents() {
        return events;
    }
    
    public void setEvents(List<EventExtraction> events) {
        this.events = events != null ? events : new ArrayList<>();
    }
    
    public List<FactEstablishment> getFacts() {
        return facts;
    }
    
    public void setFacts(List<FactEstablishment> facts) {
        this.facts = facts != null ? facts : new ArrayList<>();
    }
    
    public List<KnowledgeTransfer> getKnowledgeTransfers() {
        return knowledgeTransfers;
    }
    
    public void setKnowledgeTransfers(List<KnowledgeTransfer> knowledgeTransfers) {
        this.knowledgeTransfers = knowledgeTransfers != null ? knowledgeTransfers : new ArrayList<>();
    }
    
    public List<SecretRevelation> getSecretRevelations() {
        return secretRevelations;
    }
    
    public void setSecretRevelations(List<SecretRevelation> secretRevelations) {
        this.secretRevelations = secretRevelations != null ? secretRevelations : new ArrayList<>();
    }
    
    public List<TensionChange> getTensionChanges() {
        return tensionChanges;
    }
    
    public void setTensionChanges(List<TensionChange> tensionChanges) {
        this.tensionChanges = tensionChanges != null ? tensionChanges : new ArrayList<>();
    }
    
    public List<CharacterAppearance> getCharacterAppearances() {
        return characterAppearances;
    }
    
    public void setCharacterAppearances(List<CharacterAppearance> characterAppearances) {
        this.characterAppearances = characterAppearances != null ? characterAppearances : new ArrayList<>();
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if any changes were extracted
     */
    public boolean hasAnyChanges() {
        return !events.isEmpty()
            || !facts.isEmpty()  // NEW
            || !knowledgeTransfers.isEmpty() 
            || !secretRevelations.isEmpty()
            || !tensionChanges.isEmpty()
            || !characterAppearances.isEmpty();
    }
    
    /**
     * Get total count of all changes
     */
    public int getTotalChangeCount() {
        return events.size()
            + facts.size()  // NEW
            + knowledgeTransfers.size() 
            + secretRevelations.size()
            + tensionChanges.size()
            + characterAppearances.size();
    }
    
    @Override
    public String toString() {
        return String.format("ExtractionResult[events=%d, facts=%d, knowledge=%d, secrets=%d, tensions=%d, appearances=%d]",
            events.size(), facts.size(), knowledgeTransfers.size(), secretRevelations.size(), 
            tensionChanges.size(), characterAppearances.size());
    }
    
}