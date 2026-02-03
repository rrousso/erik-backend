package com.github.rrousso.erik_core.services.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_core.persistence.entities.CharacterKnowledge;
import com.github.rrousso.erik_core.persistence.entities.CharacterSecretState;
import com.github.rrousso.erik_core.persistence.entities.Fact;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_core.persistence.entities.Tension;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRepository;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;
import com.github.rrousso.erik_core.util.FactUtility;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles explicit commands prefixed with "/".
 * These bypass LLM processing entirely for deterministic operations.
 * 
 * Commands:
 * /help              				- Show this help message
 * /list              				- List all saved stanzas (ID + quick synopsis)
 * /search [keywords] 		     	- Search stanzas by keywords
 * /search [section]: [keywords] 	- Search on specific stanza sections by keywords
 *									  Available sections: setting-premise-tone-character					
 * /load [id]         				- Load a stanza into Erik's memory for reference
 * /clear             				- Clear loaded stanza memory
 */
@Service
public class CommandService {
    
    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final String COMMAND_PREFIX = "/";
    
    private final StanzaRepository stanzaRepository;
    
    private final StanzaPersistenceService persistenceService;

    public CommandService(StanzaRepository stanzaRepository,
    		StanzaPersistenceService persistenceService) {
    	this.persistenceService = persistenceService;
        this.stanzaRepository = stanzaRepository;
    }
    
    /**
     * Process input and check if it's a command.
     * 
     * @param userInput The raw user input
     * @param state Current session state (may be modified by commands like /load)
     * @return CommandResult indicating if handled and the response
     */
    public CommandResult processCommand(String userInput, SessionState state) {
        if (userInput == null || !userInput.startsWith(COMMAND_PREFIX)) {
            return CommandResult.notACommand();
        }
        
        String commandLine = userInput.substring(COMMAND_PREFIX.length()).trim();
        
        if (commandLine.isEmpty()) {
            return CommandResult.handled("[System] Empty command. Type /help for available commands.");
        }
        
        // Parse command and arguments
        String[] parts = commandLine.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";
        
        log.info("Processing command: {} with args: {}", command, args);
        
        return switch (command) {
            case "help" -> handleHelp();
            case "list" -> handleList();
            case "search" -> handleSearch(args);
            case "load" -> handleLoad(args, state);
            case "clear" -> handleClear(state);
            case "debug" -> handleDebug(state);
            default -> CommandResult.handled("[System] Unknown command: /" + command + ". Type /help for available commands.");
        };
    }
    
    // ========== COMMAND HANDLERS ==========
    
    private CommandResult handleHelp() {
        String help = """
            
            === ERIK COMMANDS ===
            
            /help              				- Show this help message
            /list              				- List all saved stanzas (ID + quick synopsis)
            /search [keywords] 		     	- Search stanzas by keywords
            /search [section]: [keywords] 	- Search on specific stanza sections by keywords. Available sections: setting-premise-tone-character					
            /load [id]         				- Load a stanza into Erik's memory for reference
            /clear             				- Clear loaded stanza memory
            /debug                          - Show current stanza state (facts, secrets, knowledge)
            
            Examples:
              /search vampire romance
              /load 5
              /debug
            """;
        return CommandResult.handled(help);
    }
    
    private CommandResult handleList() {
        List<Stanza> stanzas = stanzaRepository.findAll();
        
        if (stanzas.isEmpty()) {
            return CommandResult.handled("\n[System] No stanzas saved yet.\n");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== SAVED STANZAS ===\n\n");
        
        for (Stanza stanza : stanzas) {
            sb.append(formatStanzaSummary(stanza));
            sb.append("\n---\n");
        }
        
        sb.append("\nUse /load [id] to load a stanza into Erik's memory.\n");
        
        return CommandResult.handled(sb.toString());
    }
    
    private CommandResult handleSearch(String args) {
        if (args.isEmpty()) {
            return CommandResult.handled("[System] Usage: /search [keywords] or /search:field [keywords]\nExample: /search vampire romance\nExample: /search:character Kael");
        }

        String[] parts = args.split(":", 2);
        
        String searchType;
        String rawKeywords;

        if (parts.length == 1) {
            // No colon: /search vampire romance
            searchType = "full";
            rawKeywords = parts[0].trim();
        } else {
            // Has colon: /search:setting castle
            searchType = parts[0].trim().toLowerCase();
            rawKeywords = parts[1].trim();
        }

        if (rawKeywords.isEmpty()) {
            return CommandResult.handled("[System] Please provide search keywords.");
        }

        List<Stanza> matches;

        switch (searchType) {
            case "full" -> {
                // Full-text needs "word1 & word2" format
                String[] words = rawKeywords.split("\\s+");
                String query = String.join(" & ", words);
                matches = stanzaRepository.fullTextSearch(query);
            }
            case "setting" -> matches = stanzaRepository.searchBySetting(rawKeywords);
            case "premise" -> matches = stanzaRepository.searchByPremise(rawKeywords);
            case "tone" -> matches = stanzaRepository.searchByTone(rawKeywords);
            case "character" -> matches = stanzaRepository.searchByCharacter(rawKeywords);
            default -> {
                return CommandResult.handled("\n[System] '" + searchType + "' is not a valid search type.\nValid types: setting, premise, tone, character\n");
            }
        }

        if (matches.isEmpty()) {
            return CommandResult.handled("\n[System] No stanzas found matching: " + rawKeywords + "\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== SEARCH RESULTS FOR: ").append(rawKeywords).append(" ===\n\n");
        sb.append("Found ").append(matches.size()).append(" stanza(s):\n\n");

        for (Stanza stanza : matches) {
            sb.append(formatStanzaSummary(stanza));
            sb.append("\n---\n");
        }

        sb.append("\nUse /load [id] to load a stanza into Erik's memory.\n");

        return CommandResult.handled(sb.toString());
    }
    
    private CommandResult handleLoad(String idArg, SessionState state) {
        if (idArg.isEmpty()) {
            return CommandResult.handled("[System] Usage: /load [id]\nExample: /load 5");
        }
        
        Long id;
        try {
            id = Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            return CommandResult.handled("[System] Invalid ID: " + idArg + ". Must be a number.");
        }
        
        Stanza stanza;
        try {
            stanza = persistenceService.loadStanzaWithRelationships(id);  // ← FIX: loads all relationships
        } catch (IllegalArgumentException e) {
            return CommandResult.handled("[System] No stanza found with ID: " + id);
        }
        
        // Store the loaded stanza in session state for Erik to reference
        state.setLoadedStanzaMemory(stanza);
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n[System] Loaded stanza #").append(id).append(" into Erik's memory.\n\n");
        sb.append("Setting: ").append(stanza.getSetting()).append("\n");
        sb.append("Premise: ").append(stanza.getPremise()).append("\n\n");
        sb.append("Erik can now reference this stanza in your conversation.\n");
        sb.append("Use /clear to remove it from memory.\n");
        
        log.info("Loaded stanza {} into session memory", id);
        
        return CommandResult.handled(sb.toString());
    }
    
    private CommandResult handleClear(SessionState state) {
        if (state.getLoadedStanzaMemory() == null) {
            return CommandResult.handled("[System] No stanza currently loaded in memory.");
        }
        
        state.setLoadedStanzaMemory(null);
        return CommandResult.handled("[System] Cleared stanza memory.");
    }
    
    private CommandResult handleDebug(SessionState state) {
        Long activeStanzaId = state.getActiveStanzaId();
        
        if (activeStanzaId == null) {
            return CommandResult.handled("\n[Debug] No active stanza. Start a stanza first with 'let's begin'.\n");
        }
        
        // Load the stanza with all relationships
        Stanza activeStanza;
        try {
            activeStanza = persistenceService.loadStanzaWithRelationships(activeStanzaId);
        } catch (Exception e) {
            return CommandResult.handled("\n[Debug] Error loading stanza: " + e.getMessage() + "\n");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════════════╗\n");
        sb.append("║              STANZA DEBUG - CURRENT STATE                    ║\n");
        sb.append("╚═══════════════════════════════════════════════════════════════╝\n\n");
        
        // Basic info
        sb.append("ID: ").append(activeStanza.getId()).append("\n");
        sb.append("Status: ").append(activeStanza.getStatus()).append("\n");
        sb.append("Current Beat: ").append(activeStanza.getCurrentBeatNumber()).append("\n");
        sb.append("Current Exchange: ").append(activeStanza.getCurrentExchange()).append("\n\n");
        
        // FACTS REGISTRY
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ FACTS REGISTRY                                              │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n");
        
        if (activeStanza.getFacts().isEmpty()) {
            sb.append("  (No facts recorded)\n\n");
        } else {
            // Group by kind
            Map<String, List<Fact>> factsByKind = activeStanza.getFacts().stream()
                .collect(Collectors.groupingBy(Fact::getKind));
            
            for (String kind : new String[]{"USER_PRIVATE", "USER_PUBLIC", "WORLD", "OBSERVED"}) {
                if (!factsByKind.containsKey(kind)) continue;
                
                sb.append("\n  ").append(kind).append(":\n");
                for (Fact fact : factsByKind.get(kind)) {
                    String hash = FactUtility.extractHash(fact.getFactKey());
                    sb.append("    [").append(hash).append("] ").append(fact.getPredicate());
                    
                    if (fact.getCreatedBeat() != null && fact.getCreatedBeat() > 0) {
                        sb.append(" (beat ").append(fact.getCreatedBeat()).append(")");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        
        // CHARACTERS & KNOWLEDGE
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ CHARACTERS & KNOWLEDGE                                      │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");
        
        if (activeStanza.getCharacters().isEmpty()) {
            sb.append("  (No characters)\n\n");
        } else {
            for (StanzaCharacter character : activeStanza.getCharacters()) {
                sb.append("  ").append(character.getName());
                if (character.isUser()) {
                    sb.append(" (USER)");
                }
                sb.append(" [").append(character.getPresenceStatus()).append("]\n");
                
                // What they know
                if (!character.getKnownFacts().isEmpty()) {
                    sb.append("    Knows (").append(character.getKnownFacts().size()).append(" facts):\n");
                    for (CharacterKnowledge ck : character.getKnownFacts()) {
                        String hash = FactUtility.extractHash(ck.getFact().getFactKey());
                        sb.append("      [").append(hash).append("] ")
                          .append(ck.getFact().getPredicate())
                          .append(" (").append(ck.getHow()).append(")\n");
                    }
                }
                
                // Secrets they don't know
                List<CharacterSecretState> unknownSecrets = character.getSecretStates().stream()
                    .filter(css -> "UNAWARE".equals(css.getState()))
                    .collect(Collectors.toList());
                
                if (!unknownSecrets.isEmpty()) {
                    sb.append("    Does NOT know (").append(unknownSecrets.size()).append(" secrets):\n");
                    for (CharacterSecretState css : unknownSecrets) {
                        String hash = FactUtility.extractHash(css.getSecret().getFact().getFactKey());
                        sb.append("      [").append(hash).append("] SECRET: ")
                          .append(css.getSecret().getFact().getPredicate()).append("\n");
                    }
                }
                
                // Secrets they suspect
                List<CharacterSecretState> suspectedSecrets = character.getSecretStates().stream()
                    .filter(css -> "SUSPICIOUS".equals(css.getState()))
                    .collect(Collectors.toList());
                
                if (!suspectedSecrets.isEmpty()) {
                    sb.append("    Suspects (").append(suspectedSecrets.size()).append(" secrets):\n");
                    for (CharacterSecretState css : suspectedSecrets) {
                        String hash = FactUtility.extractHash(css.getSecret().getFact().getFactKey());
                        sb.append("      [").append(hash).append("] SECRET: ")
                          .append(css.getSecret().getFact().getPredicate()).append("\n");
                    }
                }
                
                sb.append("\n");
            }
        }
        
        // TENSIONS
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ ACTIVE TENSIONS                                             │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");
        
        List<Tension> activeTensions = activeStanza.getTensions().stream()
            .filter(t -> !"RESOLVED".equals(t.getStatus()))
            .collect(Collectors.toList());
        
        if (activeTensions.isEmpty()) {
            sb.append("  (No active tensions)\n\n");
        } else {
            for (Tension tension : activeTensions) {
                sb.append("  ▸ ").append(tension.getDescription()).append("\n");
                sb.append("    Pressure: ").append(tension.getPressure()).append("/10");
                if (tension.getInvolvedCharacters() != null) {
                    sb.append(" | Involves: ").append(tension.getInvolvedCharacters());
                }
                sb.append("\n\n");
            }
        }
        
        // RECENT EVENTS
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ RECENT EVENTS (last 5)                                      │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");
        
        List<StanzaEvent> recentEvents = activeStanza.getEvents().stream()
            .sorted((e1, e2) -> {
                int beatCompare = e2.getBeatNumber().compareTo(e1.getBeatNumber());
                if (beatCompare != 0) return beatCompare;
                return e2.getExchangeNumber().compareTo(e1.getExchangeNumber());
            })
            .limit(5)
            .collect(Collectors.toList());
        
        if (recentEvents.isEmpty()) {
            sb.append("  (No events recorded)\n\n");
        } else {
            for (StanzaEvent event : recentEvents) {
                sb.append("  [Beat ").append(event.getBeatNumber())
                  .append(", Ex ").append(event.getExchangeNumber())
                  .append("] ").append(event.getDescription()).append("\n");
            }
            sb.append("\n");
        }
        
        // DUPLICATION ANALYSIS
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ DUPLICATION DETECTION                                       │\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");
        
        Map<String, Long> predicateCounts = activeStanza.getFacts().stream()
            .collect(Collectors.groupingBy(Fact::getPredicate, Collectors.counting()));
        
        List<Map.Entry<String, Long>> duplicates = predicateCounts.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .collect(Collectors.toList());
        
        if (duplicates.isEmpty()) {
            sb.append("  ✓ No duplicate predicates detected\n\n");
        } else {
            sb.append("  ⚠ WARNING: Potential duplicates found:\n\n");
            for (Map.Entry<String, Long> dup : duplicates) {
                sb.append("    \"").append(dup.getKey()).append("\" appears ")
                  .append(dup.getValue()).append(" times\n");
                
                // Show the different hashes
                activeStanza.getFacts().stream()
                    .filter(f -> f.getPredicate().equals(dup.getKey()))
                    .forEach(f -> {
                        String hash = FactUtility.extractHash(f.getFactKey());
                        sb.append("      [").append(hash).append("] created beat ")
                          .append(f.getCreatedBeat()).append("\n");
                    });
                sb.append("\n");
            }
        }
        
        sb.append("═══════════════════════════════════════════════════════════════\n");
        
        return CommandResult.handled(sb.toString());
    }
    
    // ========== HELPER METHODS ==========
    
    
    private String formatStanzaSummary(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(stanza.getId()).append("\n");
        
        if (stanza.getSetting() != null && !stanza.getSetting().isEmpty()) {
            sb.append("Setting: ").append(stanza.getSetting()).append("\n");
        }
        if (stanza.getPremise() != null && !stanza.getPremise().isEmpty()) {
            sb.append("Premise: ").append(stanza.getPremise()).append("\n");
        }
        if (stanza.getTone() != null && !stanza.getTone().isEmpty()) {
            sb.append("Tone: ").append(stanza.getTone()).append("\n");
        }
        if (stanza.getCreatedAt() != null) {
            sb.append("Created: ").append(stanza.getCreatedAt().toLocalDate()).append("\n");
        }
        if (stanza.getQuickSynopsis() != null && !stanza.getQuickSynopsis().isEmpty()) {
            sb.append("\nSynopsis:\n").append(truncate(stanza.getQuickSynopsis(), 200)).append("\n");
        }
        
        return sb.toString();
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}