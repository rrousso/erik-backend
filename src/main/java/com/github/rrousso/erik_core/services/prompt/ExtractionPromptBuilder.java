package com.github.rrousso.erik_core.services.prompt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.persistence.entities.Tension;
import com.github.rrousso.erik_core.util.FactUtility;

/**
 * Builds extraction prompts by filling in the state_extraction.txt template
 * with current database state.
 * 
 * POST-REFACTOR: Now works with unified CharacterKnowledge entity.
 * - KNOWS: Character knows the fact
 * - SUSPICIOUS: Character suspects the fact
 * - UNAWARE: No CharacterKnowledge record exists (implicit)
 * 
 * This service formats JPA entities into readable text for the analytical LLM
 * to understand current state before extracting changes.
 */
@Service
public class ExtractionPromptBuilder {
    
    private static final Logger log = LoggerFactory.getLogger(ExtractionPromptBuilder.class);
    
    private final PromptLoaderService promptLoader;
    private String extractionTemplate;
    
    public ExtractionPromptBuilder(PromptLoaderService promptLoader) {
        this.promptLoader = promptLoader;
    }
    
    /**
     * Load the extraction template at startup
     */
    @jakarta.annotation.PostConstruct
    public void loadTemplate() {
        log.info("Loading extraction prompt template");
        this.extractionTemplate = promptLoader.load("analytical/state_extraction.txt");
    }
    
    /**
     * Build a complete extraction prompt for a given exchange
     * 
     * @param stanza The stanza entity (with all relationships loaded)
     * @param userInput What the user typed
     * @param narratorResponse What the narrator said
     * @return Complete prompt ready to send to Gemini
     */
    public String buildPrompt(Stanza stanza, String userInput, String narratorResponse) {
        log.debug("Building extraction prompt for stanza {}", stanza.getId());
        
        // Format the current state sections
        String charactersSection = formatCharacters(stanza);
        String tensionsSection = formatTensions(stanza);
        String recentEventsSection = formatRecentEvents(stanza);
        String factRegistrySection = formatFactRegistry(stanza);
        
        // Replace placeholders in template
        String prompt = extractionTemplate
            .replace("{characters}", charactersSection)
            .replace("{tensions}", tensionsSection)
            .replace("{recent_events}", recentEventsSection)
            .replace("{fact_registry}", factRegistrySection)
            .replace("{user_input}", userInput)
            .replace("{narrator_response}", narratorResponse);
        
        return prompt;
    }
    
    // ========== FORMATTING METHODS ==========
    
    /**
     * Format all characters with their knowledge state.
     * 
     * Shows three categories:
     * 1. KNOWS - facts they know (awarenessState = KNOWS)
     * 2. SUSPICIOUS - facts they suspect (awarenessState = SUSPICIOUS)
     * 3. UNAWARE - restricted facts they don't know (no CharacterKnowledge record)
     */
    private String formatCharacters(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        
        List<StanzaCharacter> characters = stanza.getCharacters();
        if (characters.isEmpty()) {
            return "[No characters defined]";
        }
        
        // Get all restricted facts for UNAWARE detection
        List<Fact> restrictedFacts = stanza.getFacts().stream()
            .filter(Fact::isRestricted)
            .collect(Collectors.toList());
        
        for (StanzaCharacter character : characters) {
            sb.append("---\n");
            sb.append("Name: ").append(character.getName()).append("\n");
            sb.append("Presence: ").append(character.getPresenceStatus()).append("\n");
            
            if (character.isUser()) {
                sb.append("Role: USER CHARACTER\n");
                if (character.getPublicRole() != null && !character.getPublicRole().isEmpty()) {
                    sb.append("Public Role: ").append(character.getPublicRole()).append("\n");
                }
            } else {
                if (character.getCanonRole() != null && !character.getCanonRole().isEmpty()) {
                    sb.append("Role: ").append(character.getCanonRole()).append("\n");
                }
            }
            
            // Get character's knowledge records
            List<CharacterKnowledge> allKnowledge = character.getKnownFacts();
            
            // Split by awareness state
            List<CharacterKnowledge> knownFacts = allKnowledge.stream()
                .filter(ck -> "KNOWS".equals(ck.getAwarenessState()))
                .collect(Collectors.toList());
                
            List<CharacterKnowledge> suspectedFacts = allKnowledge.stream()
                .filter(ck -> "SUSPICIOUS".equals(ck.getAwarenessState()))
                .collect(Collectors.toList());
            
            // Build set of fact IDs this character knows or suspects
            Set<Long> trackedFactIds = allKnowledge.stream()
                .map(ck -> ck.getFact().getId())
                .collect(Collectors.toSet());
            
            // Determine UNAWARE facts: restricted facts not in trackedFactIds
            List<Fact> unawareFacts = restrictedFacts.stream()
                .filter(fact -> !trackedFactIds.contains(fact.getId()))
                .collect(Collectors.toList());
            
            // Display KNOWS
            if (!knownFacts.isEmpty()) {
                sb.append("Knows:\n");
                for (CharacterKnowledge ck : knownFacts) {
                    String hash = FactUtility.extractHash(ck.getFact().getFactKey());
                    sb.append("  - [").append(hash).append("] ")
                      .append(ck.getFact().getPredicate())
                      .append(" [via ").append(ck.getHow()).append("]\n");
                }
            }
            
            // Display SUSPICIOUS
            if (!suspectedFacts.isEmpty()) {
                sb.append("Suspects:\n");
                for (CharacterKnowledge ck : suspectedFacts) {
                    String hash = FactUtility.extractHash(ck.getFact().getFactKey());
                    sb.append("  - [").append(hash).append("] ")
                      .append(ck.getFact().getPredicate()).append("\n");
                }
            }
            
            // Display UNAWARE (restricted facts character doesn't know)
            if (!unawareFacts.isEmpty()) {
                sb.append("Does NOT know:\n");
                for (Fact fact : unawareFacts) {
                    String hash = FactUtility.extractHash(fact.getFactKey());
                    sb.append("  - [").append(hash).append("] ")
                      .append(fact.getPredicate())
                      .append(" [restricted: ").append(fact.getAllowedRevealModes()).append("]\n");
                }
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Format active tensions
     */
    private String formatTensions(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        
        List<Tension> tensions = stanza.getTensions();
        if (tensions.isEmpty()) {
            return "[No tensions defined]";
        }
        
        for (Tension tension : tensions) {
            // Only show active tensions
            if (!"active".equalsIgnoreCase(tension.getStatus())) {
                continue;
            }
            
            sb.append("---\n");
            sb.append("Description: ").append(tension.getDescription()).append("\n");
            sb.append("Pressure: ").append(tension.getPressure()).append("/10\n");
            
            String[] involvedChars = tension.getInvolvedCharactersArray();
            if (involvedChars != null && involvedChars.length > 0) {
                sb.append("Involves: ").append(String.join(", ", involvedChars)).append("\n");
            }
            
            sb.append("\n");
        }
        
        if (sb.length() == 0) {
            return "[No active tensions]";
        }
        
        return sb.toString();
    }
    
    /**
     * Format recent events (last 5)
     */
    private String formatRecentEvents(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        
        List<StanzaEvent> events = stanza.getEvents();
        if (events.isEmpty()) {
            return "[No events recorded yet]";
        }
        
        // Get last 5 events (or all if fewer than 5)
        int startIndex = Math.max(0, events.size() - 5);
        List<StanzaEvent> recentEvents = events.subList(startIndex, events.size());
        
        for (StanzaEvent event : recentEvents) {
            sb.append("- [Exchange ").append(event.getExchangeNumber()).append("] ");
            sb.append(event.getDescription());
            sb.append(" (").append(event.isMajor() ? "MAJOR" : "MINOR").append(")\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Format all facts in the stanza as a reference registry.
     * 
     * This allows Gemini to see what facts already exist and reference them
     * by hash instead of creating duplicates.
     * 
     * Facts are separated into:
     * - RESTRICTED: Facts with allowedRevealModes (need special conditions to learn)
     * - PUBLIC: Facts without restrictions (observable by anyone)
     */
    private String formatFactRegistry(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        
        List<Fact> facts = stanza.getFacts();
        if (facts.isEmpty()) {
            return "[No facts recorded yet]";
        }
        
        // Separate restricted from public facts
        List<Fact> restrictedFacts = facts.stream()
            .filter(Fact::isRestricted)
            .collect(Collectors.toList());
            
        List<Fact> publicFacts = facts.stream()
            .filter(f -> !f.isRestricted())
            .collect(Collectors.toList());
        
        // Show restricted facts first (these are the important ones to track)
        if (!restrictedFacts.isEmpty()) {
            sb.append("RESTRICTED FACTS (require specific reveal modes to learn):\n");
            for (Fact fact : restrictedFacts) {
                String hash = FactUtility.extractHash(fact.getFactKey());
                sb.append("  [").append(hash).append("] ")
                  .append(fact.getPredicate())
                  .append(" [reveal: ").append(fact.getAllowedRevealModes()).append("]");
                
                if (fact.getCreatedBeat() != null && fact.getCreatedBeat() > 0) {
                    sb.append(" (emerged beat ").append(fact.getCreatedBeat()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        // Show public/observable facts
        if (!publicFacts.isEmpty()) {
            sb.append("PUBLIC FACTS (observable, no restrictions):\n");
            for (Fact fact : publicFacts) {
                String hash = FactUtility.extractHash(fact.getFactKey());
                sb.append("  [").append(hash).append("] ")
                  .append(fact.getPredicate());
                
                if (fact.getCreatedBeat() != null && fact.getCreatedBeat() > 0) {
                    sb.append(" (emerged beat ").append(fact.getCreatedBeat()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}