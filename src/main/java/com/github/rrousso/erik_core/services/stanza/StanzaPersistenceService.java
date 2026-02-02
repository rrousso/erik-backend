package com.github.rrousso.erik_core.services.stanza;

import java.util.ArrayList;
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
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.dto.initialization.NarrativeTension;
import com.github.rrousso.erik_core.dto.initialization.UserCharacter;
import com.github.rrousso.erik_core.dto.initialization.WorldContext;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.CharacterSecretState;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.entities.Secret;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.persistence.entities.Tension;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRepository;

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
        createUserPrivateFacts(stanza, initialized);
        
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
            saved.getSecrets().size(),
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
        
        if (charData.getCurrentMotivations() != null) {
            character.setMotivations(charData.getCurrentMotivations().toArray(new String[0]));
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
     * Create facts and secrets for user's private information.
     * 
     * User's privateBackstory becomes USER_PRIVATE facts with secrets.
     * Items in character's doesNotKnow lists that reference user become secrets.
     */
    private void createUserPrivateFacts(Stanza stanza, InitializedStanza initialized) {
        UserCharacter user = initialized.getUserCharacter();
        if (user == null) return;
        
        // Create facts from user's knownFacts (these are public, user knows them)
        for (String factText : user.getKnownFacts()) {
            String factKey = generateFactKey(factText, "user_knows");
            createFact(stanza, factKey, "USER_PUBLIC", "user", null, "knows", factText, "USER_SAID");
        }
        
        // Private backstory becomes a single large fact (or could be split)
        if (user.getPrivateBackstory() != null && !user.getPrivateBackstory().isEmpty()) {
            String factKey = "user_private_backstory";
            Fact backstoryFact = createFact(stanza, factKey, "USER_PRIVATE", "user", null, 
                "has_backstory", user.getPrivateBackstory(), "USER_SAID");
            
            // Create a secret for the backstory
            createSecret(stanza, backstoryFact, false, "TOLD,OBSERVED");
        }
        
        // Scan all characters' doesNotKnow lists for user-related secrets
        List<String> userSecrets = collectUserSecrets(initialized);
        for (String secretText : userSecrets) {
            String factKey = generateFactKey(secretText, "user_secret");
            
            // Check if fact already exists
            if (!factsByKey.containsKey(factKey)) {
                Fact fact = createFact(stanza, factKey, "USER_PRIVATE", "user", null, 
                    "secret", secretText, "ARCHITECT_DERIVED");
                
                // Determine if inferable based on content
                boolean inferable = isLikelyInferable(secretText);
                String revealModes = inferable ? "TOLD,OBSERVED,INFERRED,SENSED_SPECIAL" : "TOLD,OBSERVED";
                createSecret(stanza, fact, inferable, revealModes);
            }
        }
    }
    
    /**
     * Collect all unique "doesNotKnow" items that reference the user
     */
    private List<String> collectUserSecrets(InitializedStanza initialized) {
        List<String> secrets = new ArrayList<>();
        
        for (var charData : initialized.getExplicitCharacters()) {
            for (String item : charData.getDoesNotKnow()) {
                if (!secrets.contains(item)) {
                    secrets.add(item);
                }
            }
        }
        
        for (var charData : initialized.getLikelyCharacters()) {
            for (String item : charData.getDoesNotKnow()) {
                if (!secrets.contains(item)) {
                    secrets.add(item);
                }
            }
        }
        
        return secrets;
    }
    
    /**
     * Link character knowledge after all chars and facts exist
     */
    private void linkCharacterKnowledge(Stanza stanza, InitializedStanza initialized) {
        // Process explicit characters
        for (var charData : initialized.getExplicitCharacters()) {
            StanzaCharacter character = findCharacterByName(stanza, charData.getName());
            if (character != null) {
                linkKnowledgeForCharacter(stanza, character, charData.getCurrentKnowledge());
                linkSecretStatesForCharacter(stanza, character, charData.getDoesNotKnow());
            }
        }
        
        // Process likely characters
        for (var charData : initialized.getLikelyCharacters()) {
            StanzaCharacter character = findCharacterByName(stanza, charData.getName());
            if (character != null) {
                linkKnowledgeForCharacter(stanza, character, charData.getCurrentKnowledge());
                linkSecretStatesForCharacter(stanza, character, charData.getDoesNotKnow());
            }
        }
    }
    
    /**
     * Create CharacterKnowledge records for facts the character knows
     */
    private void linkKnowledgeForCharacter(Stanza stanza, StanzaCharacter character, List<String> knowledgeItems) {
        for (String item : knowledgeItems) {
            // Create or find fact for this knowledge
            String factKey = generateFactKey(item, "char_knows");
            Fact fact = factsByKey.get(factKey);
            
            if (fact == null) {
                // Create new fact (this is something the character knows but isn't a user secret)
                fact = createFact(stanza, factKey, "WORLD", "world", null, "truth", item, "DOCUMENTED");
            }
            
            // Create knowledge link
            CharacterKnowledge knowledge = new CharacterKnowledge(character, fact, "DOCUMENTED");
            knowledge.setStatus("LEARNED");
            knowledge.setLearnedBeat(0);
            knowledge.setLearnedExchange(0);
            character.getKnownFacts().add(knowledge);
        }
    }
    
    /**
     * Create CharacterSecretState records for secrets the character doesn't know about
     */
    private void linkSecretStatesForCharacter(Stanza stanza, StanzaCharacter character, List<String> doesNotKnow) {
        for (String item : doesNotKnow) {
            // Find the secret that locks this fact
            String factKey = generateFactKey(item, "user_secret");
            Fact fact = factsByKey.get(factKey);
            
            if (fact != null) {
                // Find secret for this fact
                Secret secret = findSecretForFact(stanza, fact);
                if (secret != null) {
                    // Create secret state - character is UNAWARE
                    CharacterSecretState state = new CharacterSecretState();
                    state.setCharacter(character);
                    state.setSecret(secret);
                    state.setState("UNAWARE");
                    character.getSecretStates().add(state);
                }
            }
        }
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Create a fact and track it
     */
    private Fact createFact(Stanza stanza, String factKey, String kind, 
            String subjectType, String subjectId, String predicate, 
            String value, String source) {
        
        // Check if already exists
        if (factsByKey.containsKey(factKey)) {
            return factsByKey.get(factKey);
        }
        
        Fact fact = new Fact(stanza, factKey, kind, predicate);
        fact.setSubjectType(subjectType);
        fact.setSubjectId(subjectId);
        fact.setFactValue(wrapAsJson(value));
        fact.setSource(source);
        fact.setCreatedBeat(0);
        fact.setCreatedExchange(0);
        
        stanza.getFacts().add(fact);
        factsByKey.put(factKey, fact);
        
        return fact;
    }
    
    /**
     * Create a secret for a fact
     */
    private Secret createSecret(Stanza stanza, Fact fact, boolean inferable, String allowedModes) {
        Secret secret = new Secret(stanza, fact, inferable, allowedModes);
        stanza.getSecrets().add(secret);
        return secret;
    }
    
    /**
     * Generate a fact key from text (max 50 chars, snake_case)
     */
    private String generateFactKey(String text, String prefix) {
        // Normalize: lowercase, replace spaces/special chars with underscore
        String normalized = text.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "")  // Trim leading/trailing underscores
            .replaceAll("__+", "_");   // Collapse multiple underscores
        
        // Prefix and truncate to 50 chars
        String key = prefix + "_" + normalized;
        if (key.length() > 50) {
            key = key.substring(0, 50);
        }
        
        // Handle collisions by appending number if key exists
        String baseKey = key;
        int counter = 1;
        while (factsByKey.containsKey(key)) {
            key = baseKey.substring(0, Math.min(baseKey.length(), 47)) + "_" + counter;
            counter++;
        }
        
        return key;
    }
    
    /**
     * Wrap a value as simple JSON string
     */
    private String wrapAsJson(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }
    
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
     * Find the secret that locks a fact
     */
    private Secret findSecretForFact(Stanza stanza, Fact fact) {
        return stanza.getSecrets().stream()
            .filter(s -> s.getFact().getFactKey().equals(fact.getFactKey()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Determine if a secret is likely inferable from other facts
     */
    private boolean isLikelyInferable(String secretText) {
        String lower = secretText.toLowerCase();
        
        // Things that are typically NOT inferable (must be told/observed)
        if (lower.contains("trans") || 
            lower.contains("secret") ||
            lower.contains("hidden") ||
            lower.contains("trauma") ||
            lower.contains("family history")) {
            return false;
        }
        
        // Things that might be inferable from behavior
        if (lower.contains("supernatural") ||
            lower.contains("magic") ||
            lower.contains("powers") ||
            lower.contains("spark")) {
            return true;
        }
        
        return false;  // Default to not inferable
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
        stanza.getSecrets().size();
        stanza.getBeats().size(); 
        stanza.getEvents().size();
        
        // Also initialize nested collections on characters
        for (StanzaCharacter character : stanza.getCharacters()) {
            character.getKnownFacts().size();
            character.getSecretStates().size();
        }
        
        return stanza;
    }
}