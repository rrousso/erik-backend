package com.github.rrousso.erik_core.services.prompt;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.CharacterSecretState;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.persistence.entities.Tension;

/**
 * Builds extraction prompts by filling in the state_extraction.txt template
 * with current database state.
 * 
 * This service knows how to format JPA entities into readable text for the
 * analytical LLM to understand what the current state is before extracting changes.
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
        
        // Replace placeholders in template
        String prompt = extractionTemplate
            .replace("{characters}", charactersSection)
            .replace("{tensions}", tensionsSection)
            .replace("{recent_events}", recentEventsSection)
            .replace("{user_input}", userInput)
            .replace("{narrator_response}", narratorResponse);
        
        return prompt;
    }
    
    // ========== FORMATTING METHODS ==========
    
    /**
     * Format all characters with their knowledge state
     */
    private String formatCharacters(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        
        List<StanzaCharacter> characters = stanza.getCharacters();
        if (characters.isEmpty()) {
            return "[No characters defined]";
        }
        
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
            
            // What this character knows
            List<CharacterKnowledge> knowledge = character.getKnownFacts();
            if (!knowledge.isEmpty()) {
                sb.append("Knows:\n");
                for (CharacterKnowledge ck : knowledge) {
                    sb.append("  - ").append(ck.getFact().getPredicate());
                    if (ck.getFact().getFactValue() != null) {
                        sb.append(": ").append(ck.getFact().getFactValue());
                    }
                    sb.append(" [learned via ").append(ck.getHow()).append("]\n");
                }
            }
            
            // Secrets this character does NOT know
            List<CharacterSecretState> secretStates = character.getSecretStates();
            List<CharacterSecretState> unknownSecrets = secretStates.stream()
                .filter(css -> "UNAWARE".equals(css.getState()))
                .collect(Collectors.toList());
            
            if (!unknownSecrets.isEmpty()) {
                sb.append("Does NOT know:\n");
                for (CharacterSecretState css : unknownSecrets) {
                    String secretDesc = css.getSecret().getFact().getPredicate();
                    sb.append("  - SECRET: ").append(secretDesc).append("\n");
                }
            }
            
            // Secrets this character suspects
            List<CharacterSecretState> suspectedSecrets = secretStates.stream()
                .filter(css -> "SUSPICIOUS".equals(css.getState()))
                .collect(Collectors.toList());
            
            if (!suspectedSecrets.isEmpty()) {
                sb.append("Suspects:\n");
                for (CharacterSecretState css : suspectedSecrets) {
                    String secretDesc = css.getSecret().getFact().getPredicate();
                    sb.append("  - SECRET: ").append(secretDesc).append("\n");
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
}