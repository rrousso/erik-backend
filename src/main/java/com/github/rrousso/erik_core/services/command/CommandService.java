package com.github.rrousso.erik_core.services.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRepository;

import java.util.List;
import java.util.Optional;

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

    public CommandService(StanzaRepository stanzaRepository) {
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
            
            Examples:
              /search vampire romance
              /load 5
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
        
        Optional<Stanza> stanzaOpt = stanzaRepository.findById(id);

        if (stanzaOpt.isEmpty()) {
            return CommandResult.handled("[System] No stanza found with ID: " + id);
        }

        Stanza stanza = stanzaOpt.get();
        
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