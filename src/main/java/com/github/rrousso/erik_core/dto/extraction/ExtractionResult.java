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
    private List<FactDiscovery> factDiscoveries = new ArrayList<>();
    private List<SecretRevelation> secretRevelations = new ArrayList<>();
    private List<TensionChange> tensionChanges = new ArrayList<>();
    private List<CharacterAppearance> characterAppearances = new ArrayList<>();
    private List<BlueprintUpdate> blueprintUpdates = new ArrayList<>();
    private List<EmergentCharacterExtraction> emergentCharacters = new ArrayList<>();
    
    // === CONSTRUCTORS ===
    
    public ExtractionResult() {}
    
    // === GETTERS AND SETTERS ===
    
    public List<BlueprintUpdate> getBlueprintUpdates() {
        return blueprintUpdates;
    }
    
    public void setBlueprintUpdates(List<BlueprintUpdate> blueprintUpdates) {
        this.blueprintUpdates = blueprintUpdates != null ? blueprintUpdates : new ArrayList<>();
    }
    
    public List<EventExtraction> getEvents() {
        return events;
    }
    
    public void setEvents(List<EventExtraction> events) {
        this.events = events != null ? events : new ArrayList<>();
    }
    
    public List<FactDiscovery> getFactDiscoveries() {
        return factDiscoveries;
    }
    
    public void setFactDiscoveries(List<FactDiscovery> factDiscoveries) {
        this.factDiscoveries = factDiscoveries != null ? factDiscoveries : new ArrayList<>();
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
    
    public List<EmergentCharacterExtraction> getEmergentCharacters() {
        return emergentCharacters;
    }
    
    public void setEmergentCharacters(List<EmergentCharacterExtraction> emergentCharacters) {
        this.emergentCharacters = emergentCharacters != null ? emergentCharacters : new ArrayList<>();
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Check if any changes were extracted
     */
    public boolean hasAnyChanges() {
        return !events.isEmpty() 
            || !factDiscoveries.isEmpty()  
            || !secretRevelations.isEmpty()
            || !tensionChanges.isEmpty()
            || !characterAppearances.isEmpty()
            || !blueprintUpdates.isEmpty()
            || !emergentCharacters.isEmpty();
    }
    
    /**
     * Get total count of all changes
     */
    public int getTotalChangeCount() {
        return events.size() 
            + factDiscoveries.size()  
            + secretRevelations.size()
            + tensionChanges.size()
            + characterAppearances.size()
            + blueprintUpdates.size()
            + emergentCharacters.size();
    }
    
    @Override
    public String toString() {
        return String.format("ExtractionResult[events=%d, factDiscoveries=%d, secrets=%d, tensions=%d, appearances=%d, blueprints=%d, emergent=%d]",
            events.size(),factDiscoveries.size(), secretRevelations.size(), 
            tensionChanges.size(), characterAppearances.size(), blueprintUpdates.size(), emergentCharacters.size());
    }
    
}