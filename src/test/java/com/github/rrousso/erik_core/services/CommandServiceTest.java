package com.github.rrousso.erik_core.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.entities.CommandResult;
import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.entities.StanzaRecord;
import com.github.rrousso.erik_core.entities.Persona;
import com.github.rrousso.erik_core.repositories.StanzaRecordRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Command Service Tests")
public class CommandServiceTest {

    @Mock
    private StanzaRecordRepository stanzaRecordRepository;
    
    private CommandService commandService;
    private SessionState state;
    
    @BeforeEach
    void setUp() {
        commandService = new CommandService(stanzaRecordRepository);
        state = new SessionState();
    }
    
    // ========== NON-COMMAND INPUT ==========
    
    @Test
    @DisplayName("Should return notACommand for regular input")
    void shouldReturnNotACommandForRegularInput() {
        CommandResult result = commandService.processCommand("Hello Erik!", state);
        
        assertFalse(result.wasHandled());
        assertEquals("", result.getResponse());
    }
    
    @Test
    @DisplayName("Should return notACommand for null input")
    void shouldReturnNotACommandForNullInput() {
        CommandResult result = commandService.processCommand(null, state);
        
        assertFalse(result.wasHandled());
    }
    
    @Test
    @DisplayName("Should return notACommand for input without prefix")
    void shouldReturnNotACommandForInputWithoutPrefix() {
        CommandResult result = commandService.processCommand("list stanzas", state);
        
        assertFalse(result.wasHandled());
    }
    
    // ========== HELP COMMAND ==========
    
    @Test
    @DisplayName("Should handle /help command")
    void shouldHandleHelpCommand() {
        CommandResult result = commandService.processCommand("/help", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("ERIK COMMANDS"));
        assertTrue(result.getResponse().contains("/list"));
        assertTrue(result.getResponse().contains("/search"));
        assertTrue(result.getResponse().contains("/load"));
    }
    
    // ========== LIST COMMAND ==========
    
    @Test
    @DisplayName("Should handle /list command with no stanzas")
    void shouldHandleListCommandWithNoStanzas() {
        when(stanzaRecordRepository.findAll()).thenReturn(Collections.emptyList());
        
        CommandResult result = commandService.processCommand("/list", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas saved yet"));
    }
    
    @Test
    @DisplayName("Should handle /list command with stanzas")
    void shouldHandleListCommandWithStanzas() {
        StanzaRecord stanza = createTestStanza(1L, "Haunted mansion", "Ghost investigation");
        when(stanzaRecordRepository.findAll()).thenReturn(Arrays.asList(stanza));
        
        CommandResult result = commandService.processCommand("/list", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("SAVED STANZAS"));
        assertTrue(result.getResponse().contains("Haunted mansion"));
        assertTrue(result.getResponse().contains("Ghost investigation"));
    }
    
    // ========== SEARCH COMMAND ==========
    
    @Test
    @DisplayName("Should handle /search command with no keywords")
    void shouldHandleSearchCommandWithNoKeywords() {
        CommandResult result = commandService.processCommand("/search", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Usage: /search"));
    }
    
    @Test
    @DisplayName("Should handle /search command with no matches")
    void shouldHandleSearchCommandWithNoMatches() {
        StanzaRecord stanza = createTestStanza(1L, "Haunted mansion", "Ghost investigation");
        when(stanzaRecordRepository.findAll()).thenReturn(Arrays.asList(stanza));
        
        CommandResult result = commandService.processCommand("/search vampire", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas found matching"));
    }
    
    @Test
    @DisplayName("Should handle /search command with matches")
    void shouldHandleSearchCommandWithMatches() {
        StanzaRecord stanza1 = createTestStanza(1L, "Haunted mansion", "Ghost investigation");
        StanzaRecord stanza2 = createTestStanza(2L, "Vampire castle", "Romance with vampire");
        when(stanzaRecordRepository.findAll()).thenReturn(Arrays.asList(stanza1, stanza2));
        
        CommandResult result = commandService.processCommand("/search vampire", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("SEARCH RESULTS"));
        assertTrue(result.getResponse().contains("Vampire castle"));
        assertFalse(result.getResponse().contains("Haunted mansion"));
    }
    
    @Test
    @DisplayName("Should handle /search with multiple keywords (AND logic)")
    void shouldHandleSearchWithMultipleKeywords() {
        StanzaRecord stanza1 = createTestStanza(1L, "Vampire castle", "Horror investigation");
        StanzaRecord stanza2 = createTestStanza(2L, "Vampire beach", "Romance with vampire");
        when(stanzaRecordRepository.findAll()).thenReturn(Arrays.asList(stanza1, stanza2));
        
        CommandResult result = commandService.processCommand("/search vampire romance", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Vampire beach"));
        assertFalse(result.getResponse().contains("Vampire castle")); // No "romance" keyword
    }
    
    // ========== LOAD COMMAND ==========
    
    @Test
    @DisplayName("Should handle /load command with no ID")
    void shouldHandleLoadCommandWithNoId() {
        CommandResult result = commandService.processCommand("/load", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Usage: /load"));
    }
    
    @Test
    @DisplayName("Should handle /load command with invalid ID")
    void shouldHandleLoadCommandWithInvalidId() {
        CommandResult result = commandService.processCommand("/load abc", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Invalid ID"));
    }
    
    @Test
    @DisplayName("Should handle /load command with non-existent ID")
    void shouldHandleLoadCommandWithNonExistentId() {
        when(stanzaRecordRepository.findById(999L)).thenReturn(Optional.empty());
        
        CommandResult result = commandService.processCommand("/load 999", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanza found with ID"));
    }
    
    @Test
    @DisplayName("Should handle /load command successfully")
    void shouldHandleLoadCommandSuccessfully() {
        StanzaRecord stanza = createTestStanza(5L, "Haunted mansion", "Ghost investigation");
        when(stanzaRecordRepository.findById(5L)).thenReturn(Optional.of(stanza));
        
        CommandResult result = commandService.processCommand("/load 5", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Loaded stanza #5"));
        assertTrue(result.getResponse().contains("Haunted mansion"));
        
        // Verify state was updated
        assertNotNull(state.getLoadedStanzaMemory());
        assertEquals(5L, state.getLoadedStanzaMemory().getId());
    }
    
    // ========== CLEAR COMMAND ==========
    
    @Test
    @DisplayName("Should handle /clear command with no loaded memory")
    void shouldHandleClearCommandWithNoLoadedMemory() {
        CommandResult result = commandService.processCommand("/clear", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanza currently loaded"));
    }
    
    @Test
    @DisplayName("Should handle /clear command successfully")
    void shouldHandleClearCommandSuccessfully() {
        StanzaRecord stanza = createTestStanza(5L, "Haunted mansion", "Ghost investigation");
        state.setLoadedStanzaMemory(stanza);
        
        CommandResult result = commandService.processCommand("/clear", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Cleared stanza memory"));
        assertNull(state.getLoadedStanzaMemory());
    }
    
    // ========== UNKNOWN COMMAND ==========
    
    @Test
    @DisplayName("Should handle unknown command")
    void shouldHandleUnknownCommand() {
        CommandResult result = commandService.processCommand("/foobar", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Unknown command"));
        assertTrue(result.getResponse().contains("/help"));
    }
    
    @Test
    @DisplayName("Should handle empty command after prefix")
    void shouldHandleEmptyCommandAfterPrefix() {
        CommandResult result = commandService.processCommand("/", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Empty command"));
    }
    
    // ========== HELPER METHODS ==========
    
    private StanzaRecord createTestStanza(Long id, String setting, String premise) {
        Persona persona = new Persona("Test", "they/them", "description", "details");
        persona.setId(1L);
        
        StanzaRecord stanza = new StanzaRecord(persona, "Quick synopsis for " + premise);
        stanza.setId(id);
        stanza.setSetting(setting);
        stanza.setPremise(premise);
        stanza.setTone("horror");
        
        return stanza;
    }
}