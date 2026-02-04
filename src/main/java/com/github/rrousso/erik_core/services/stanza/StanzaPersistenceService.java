package com.github.rrousso.erik_core.services.stanza;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_core.dto.initialization.BackgroundCharacter;
import com.github.rrousso.erik_core.dto.initialization.InitFact;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.dto.initialization.NarrativeTension;
import com.github.rrousso.erik_core.dto.initialization.UserCharacter;
import com.github.rrousso.erik_core.dto.initialization.WorldContext;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.persistence.entities.Tension;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRepository;
import com.github.rrousso.erik_core.util.FactUtility;

import jakarta.transaction.Transactional;

/**
 * Service responsible for persisting stanza state to the database.
 * 
 * This is the bridge between the domain objects (InitializedStanza, etc.) 
 * and the JPA entities (Stanza, StanzaCharacter, Fact, Secret, etc.).
 * 
 * Key responsibilities:
 * 1. Map InitializedStanza → JPA entities on stanza start
 * 2. Convert "doesNotKnow" lists into Facts + Secrets
 * 3. Convert "currentKnowledge" lists into Facts + CharacterKnowledge
 * 4. Handle updates during stanza lifecycle (pause, end, etc.)
 */
@Service
public class StanzaPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(StanzaPersistenceService.class);
    
    private final StanzaRepository stanzaRepository;
    private final ObjectMapper objectMapper;
    
    // Track facts by key during mapping to avoid duplicates
    private Map<String, Fact> factsByKey;
    
    public StanzaPersistenceService(StanzaRepository stanzaRepository) {
        this.stanzaRepository = stanzaRepository;
        this.objectMapper = new ObjectMapper();
    }
    
    // ========== MAIN SAVE METHOD ==========
    
    /**
     * Save an InitializedStanza to the database.
     * Called once when a stanza starts.
     * 
     * @param initialized The parsed initialization from the architect
     * @param persona The user's persona
     * @return The persisted Stanza entity with all relationships populated
     */
    @Transactional
    public Stanza saveInitializedStanza(InitializedStanza initialized, Persona persona) {
        log.info("[Persistence] Saving initialized stanza for persona: {}", persona.getName());
        
        // Reset fact tracking for this save
        factsByKey = new HashMap<>();
        
        // 1. Create the main Stanza entity
        Stanza stanza = createStanzaEntity(initialized, persona);
        
        // 2. Create the user character
        StanzaCharacter userChar = createUserCharacter(stanza, initialized.getUserCharacter(), persona.getName());
        stanza.getCharacters().add(userChar);
        
        // 3. Create user's private facts + secrets
        createFactsFromInitialization(stanza, initialized);
        
        // 4. Create explicit characters
        for (var charData : initialized.getExplicitCharacters()) {
            StanzaCharacter character = createCharacterEntity(stanza, charData, "explicit");
            stanza.getCharacters().add(character);
        }
         
        // 5. Create likely characters
        for (var charData : initialized.getLikelyCharacters()) {
            StanzaCharacter character = createCharacterEntity(stanza, charData, "likely");
            stanza.getCharacters().add(character);
        }
        
        // 6. Create background characters
        for (var bgChar : initialized.getBackgroundCharacters()) {
            StanzaCharacter character = createBackgroundCharacterEntity(stanza, bgChar);
            stanza.getCharacters().add(character);
        }
        
        // 7. Create tensions
        for (var tensionData : initialized.getInitialTensions()) {
            Tension tension = createTensionEntity(stanza, tensionData);
            stanza.getTensions().add(tension);
        }
        
        // 8. Now link character knowledge
        linkCharacterKnowledge(stanza, initialized);
        
        // 9. Initialize first beat (NEW!)
        stanza.initializeFirstBeat();
        
        // 10. Save everything (cascade will handle children)
        Stanza saved = stanzaRepository.save(stanza);
        
        log.info("[Persistence] Stanza saved with ID: {}", saved.getId());
        log.info("[Persistence] Characters: {}, Facts: {}, Secrets: {}, Tensions: {}", 
            saved.getCharacters().size(),
            saved.getFacts().size(),
            saved.getTensions().size());
        
        return saved;
    }
    
    @Transactional
    public Stanza save(@NonNull Stanza stanza) {
        return stanzaRepository.save(stanza);
    }
    
    // ========== ENTITY CREATION METHODS ==========
    
    /**
     * Create the main Stanza entity from InitializedStanza
     */
    private Stanza createStanzaEntity(InitializedStanza initialized, Persona persona) {
        Stanza stanza = new Stanza(persona, initialized.getWorldIdentifier());
        
        // World context
        WorldContext world = initialized.getWorldContext();
        if (world != null) {
            stanza.setTimeContext(world.getTimeContext());
            stanza.setWorldState(world.getCurrentWorldState());
            
            // CRITICAL: Set tone from worldContext
            if (world.getTone() != null && !world.getTone().isEmpty()) {
                stanza.setTone(world.getTone());
                log.debug("[Persistence] Set stanza tone: {}", world.getTone());
            }
            
            // World rules as array
            if (world.getSupernaturalRules() != null) {
                stanza.setWorldRules(world.getSupernaturalRules().toArray(new String[0]));
            }
            
            // Locations as JSON
            if (world.getRelevantLocations() != null && !world.getRelevantLocations().isEmpty()) {
                try {
                    stanza.setLocations(objectMapper.writeValueAsString(world.getRelevantLocations()));
                } catch (JsonProcessingException e) {
                    log.warn("[Persistence] Failed to serialize locations: {}", e.getMessage());
                }
            }
        }
        
        // Search fields (will be populated more during stanza, but set basics now)
        UserCharacter user = initialized.getUserCharacter();
        if (user != null) {
            stanza.setSetting(user.getCurrentLocation());
        }
        
        // Premise can come from tensions or world state
        if (world != null && world.getCurrentWorldState() != null) {
            stanza.setPremise(truncate(world.getCurrentWorldState(), 500));
        }
        
        // Initial counters
        stanza.setCurrentBeat(0);
        stanza.setCurrentExchange(0);
        stanza.setStatus("active");
        
        return stanza;
    }
    
    /**
     * Create the user character entity
     */
    private StanzaCharacter createUserCharacter(Stanza stanza, UserCharacter userData, String personaName) {
        StanzaCharacter userChar = StanzaCharacter.createUserCharacter(stanza, personaName);
        
        if (userData != null) {
            userChar.setPublicRole(userData.getPublicRole());
            userChar.setPrivateBackstory(userData.getPrivateBackstory());
            userChar.setCurrentLocation(userData.getCurrentLocation());
            
            if (userData.getPubliclyVisibleTraits() != null) {
                userChar.setVisibleTraits(userData.getPubliclyVisibleTraits().toArray(new String[0]));
            }
            
            if (userData.getCurrentGoals() != null) {
                userChar.setGoals(userData.getCurrentGoals().toArray(new String[0]));
            }
        }
        
        return userChar;
    }
    
    /**
     * Create a non-user character entity from initialization data
     */
    private StanzaCharacter createCharacterEntity(
            Stanza stanza, 
            com.github.rrousso.erik_core.dto.initialization.StanzaCharacter charData,
            String tier) {
        
        StanzaCharacter character = new StanzaCharacter(stanza, charData.getName());
        character.setCanonRole(charData.getCanonRole());
        character.setEmotionalState(charData.getCurrentEmotionalState());
        character.setRelationshipToUser(charData.getRelationshipToUser());
        
        // Store blueprint data in dedicated fields
        if (charData.getBlueprint() != null) {
            var blueprint = charData.getBlueprint();
            character.setBlueprintTier1Essentials(blueprint.getTier1Essentials());
            character.setBlueprintTier2Motivators(blueprint.getTier2Motivators());
            
            if (blueprint.getTier3Anchors() != null && !blueprint.getTier3Anchors().isEmpty()) {
                character.setBlueprintTier3Anchors(blueprint.getTier3Anchors().toArray(new String[0]));
            }
        }
        
        // Presence status based on tier and presentInFirstScene
        if (charData.isPresentInFirstScene()) {
            character.setPresenceStatus("present");
        } else if ("explicit".equals(tier) || "likely".equals(tier)) {
            character.setPresenceStatus("potential");
        } else {
            character.setPresenceStatus("background");
        }
        
        return character;
    }
    
    /**
     * Create a background character (minimal data)
     */
    private StanzaCharacter createBackgroundCharacterEntity(Stanza stanza, BackgroundCharacter bgChar) {
        StanzaCharacter character = new StanzaCharacter(stanza, bgChar.getName());
        character.setCanonRole(bgChar.getCanonRole());
        character.setPresenceStatus("background");
        // Store relevance in emotional state field (repurposing for background chars)
        character.setEmotionalState(bgChar.getThreatOrAlly() + ": " + bgChar.getRelevanceToStanza());
        return character;
    }
    
    /**
     * Create a tension entity
     */
    private Tension createTensionEntity(Stanza stanza, NarrativeTension tensionData) {
        Tension tension = new Tension(stanza, tensionData.getDescription(), tensionData.getPressure());
        
        if (tensionData.getInvolvedCharacters() != null) {
            tension.setInvolvedCharacters(String.join(",", tensionData.getInvolvedCharacters()));
        }
        
        if (tensionData.getPotentialTriggers() != null) {
            tension.setPotentialTriggers(String.join("|", tensionData.getPotentialTriggers()));
        }
        
        tension.setSource(tensionData.getSource());
        tension.setCreatedBeat(0);
        
        return tension;
    }
    
    // ========== FACT/SECRET CREATION ==========
    
    /**
     * Create facts from the initialization facts list.
     * All facts are created from the structured list provided by the LLM.
     */
    private void createFactsFromInitialization(Stanza stanza, InitializedStanza initialized) {
        log.info("[Persistence] Creating {} facts from initialization", initialized.getFacts().size());
        
        for (InitFact initFact : initialized.getFacts()) {
            String factKey = FactUtility.generateFactKey(initFact.getStatement());
            
            // Check if already exists (shouldn't happen but be safe)
            if (factsByKey.containsKey(factKey)) {
                log.warn("[Persistence] Duplicate fact key during init: {}", factKey);
                continue;
            }
            
            // Create fact with statement in predicate, truth value in factValue
            Fact fact = new Fact();
            fact.setStanza(stanza);
            fact.setFactKey(factKey);
            fact.setPredicate(initFact.getStatement());
            fact.setFactValue(initFact.getTruthValue() != null ? initFact.getTruthValue().toString() : "true");
            fact.setKind(determineFactKind(initFact));
            fact.setSource("ARCHITECT_INIT");
            fact.setCreatedBeat(0);
            fact.setCreatedExchange(0);
            
            // Set discovery rules if restricted
            if (initFact.isRestricted()) {
                fact.setAllowedRevealModes(initFact.getAllowedRevealModes());
            }
            
            stanza.getFacts().add(fact);
            factsByKey.put(factKey, fact);
            
            // Also track by tempId for linking characters
            factsByKey.put(initFact.getTempId(), fact);
            
            log.debug("[Persistence] Created fact [{}]: {} (restricted: {})", 
                factKey, 
                initFact.getStatement(), 
                initFact.isRestricted());
        }
    }
    
    /**
     * Determine fact kind based on content.
     * Can be enhanced with smarter logic later.
     */
    private String determineFactKind(InitFact initFact) {
        // For now, if it's restricted, it's likely private
        // If public, it's world knowledge
        return initFact.isRestricted() ? "PRIVATE" : "WORLD";
    }
    
    
    /**
     * Link character knowledge after all facts exist.
     * 
     * NEW LOGIC:
     * - Only create CharacterKnowledge for facts the character KNOWS
     * - If no record exists, character is assumed UNAWARE (handled by prompt builder)
     */
    private void linkCharacterKnowledge(Stanza stanza, InitializedStanza initialized) {
        // Process explicit characters
        for (var charData : initialized.getExplicitCharacters()) {
            StanzaCharacter character = findCharacterByName(stanza, charData.getName());
            if (character != null) {
                linkKnowledgeForCharacter(stanza, character, charData.getKnows());
            }
        }
        
        // Process likely characters
        for (var charData : initialized.getLikelyCharacters()) {
            StanzaCharacter character = findCharacterByName(stanza, charData.getName());
            if (character != null) {
                linkKnowledgeForCharacter(stanza, character, charData.getKnows());
            }
        }
    }
    
    /**
     * Create CharacterKnowledge records for fact tempIds the character knows.
     */
    private void linkKnowledgeForCharacter(Stanza stanza, StanzaCharacter character, List<String> factTempIds) {
        for (String tempId : factTempIds) {
            // Find fact by tempId
            Fact fact = factsByKey.get(tempId);
            
            if (fact == null) {
                log.warn("[Persistence] Fact with tempId '{}' not found for character '{}'", 
                    tempId, character.getName());
                continue;
            }
            
            // Create knowledge link with KNOWS state
            CharacterKnowledge knowledge = new CharacterKnowledge();
            knowledge.setCharacter(character);
            knowledge.setFact(fact);
            knowledge.setAwarenessState("KNOWS");
            knowledge.setHow("DOCUMENTED");  // Known at init = documented
            knowledge.setStatus("LEARNED");
            knowledge.setLearnedBeat(0);
            knowledge.setLearnedExchange(0);
            
            character.getKnownFacts().add(knowledge);
            
            log.debug("[Persistence] Character '{}' knows fact [{}]", 
                character.getName(), 
                FactUtility.extractHash(fact.getFactKey()));
        }
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Find a character by name in the stanza
     */
    private StanzaCharacter findCharacterByName(Stanza stanza, String name) {
        return stanza.getCharacters().stream()
            .filter(c -> c.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
    
    
    /**
     * Truncate string to max length
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    // ========== UPDATE METHODS (for later phases) ==========
    
    /**
     * Update stanza status
     */
    @Transactional
    public Stanza updateStatus(@NonNull Long stanzaId, String newStatus) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
            .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        
        stanza.setStatus(newStatus);
        return stanzaRepository.save(stanza);
    }
    
    /**
     * Increment exchange counter
     */
    @Transactional
    public void incrementExchange(@NonNull Long stanzaId) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
            .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        
        stanza.incrementExchange();
        stanzaRepository.save(stanza);
    }
    
    /**
     * Set quick synopsis when stanza ends
     */
    @Transactional
    public void setQuickSynopsis(@NonNull Long stanzaId, String synopsis) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
            .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        
        stanza.setQuickSynopsis(synopsis);
        stanzaRepository.save(stanza);
    }
    
    @Transactional
    public Stanza loadStanzaWithRelationships(@NonNull Long stanzaId) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
            .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        
        // Force initialization of lazy collections while session is open
        stanza.getCharacters().size();
        stanza.getTensions().size();
        stanza.getFacts().size();
        stanza.getBeats().size(); 
        stanza.getEvents().size();
        
        // Also initialize nested collections on characters
        for (StanzaCharacter character : stanza.getCharacters()) {
            character.getKnownFacts().size();
        }
        
        return stanza;
    }
}