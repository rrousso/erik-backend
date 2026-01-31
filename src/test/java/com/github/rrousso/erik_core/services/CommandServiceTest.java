package com.github.rrousso.erik_core.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.services.command.CommandService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.repositories.StanzaRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Command Service Tests")
public class CommandServiceTest {

    @Mock
    private StanzaRepository stanzaRepository;
    
    @Mock
    private StanzaPersistenceService persistenceService;
    
    private CommandService commandService;
    private SessionState state;
    
    @BeforeEach
    void setUp() {
        commandService = new CommandService(stanzaRepository, persistenceService);
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
        when(stanzaRepository.findAll()).thenReturn(Collections.emptyList());
        
        CommandResult result = commandService.processCommand("/list", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas saved yet"));
    }
    
    @Test
    @DisplayName("Should handle /list command with stanzas")
    void shouldHandleListCommandWithStanzas() {
        Stanza stanza = createTestStanza(1L, "Haunted mansion", "Ghost investigation");
        when(stanzaRepository.findAll()).thenReturn(Arrays.asList(stanza));
        
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
        
        CommandResult result = commandService.processCommand("/search vampire", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas found matching"));
    }
    
    @Test
    @DisplayName("Should handle /search command with matches")
    void shouldHandleSearchCommandWithMatches() {
        Stanza stanza2 = createTestStanza(2L, "Vampire castle", "Romance with vampire");
        when(stanzaRepository.fullTextSearch("vampire")).thenReturn(Arrays.asList(stanza2));
        
        CommandResult result = commandService.processCommand("/search vampire", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("SEARCH RESULTS"));
        assertTrue(result.getResponse().contains("Vampire castle"));
    }
    
    @Test
    @DisplayName("Should handle /search with multiple keywords (AND logic)")
    void shouldHandleSearchWithMultipleKeywords() {
        Stanza stanza2 = createTestStanza(2L, "Vampire beach", "Romance with vampire");
        when(stanzaRepository.fullTextSearch("vampire & romance")).thenReturn(Arrays.asList(stanza2));
        
        CommandResult result = commandService.processCommand("/search vampire romance", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Vampire beach"));
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
    void shouldHandleLoadCommandWithNonExistentId() throws Exception{
    	doThrow(new IllegalArgumentException("Not found"))
       .when(persistenceService).loadStanzaWithRelationships(999L);
        
        CommandResult result = commandService.processCommand("/load 999", state);
        
        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("[System] No stanza found with ID: 999"));
    }
    
    @Test
    @DisplayName("Should handle /load command successfully")
    void shouldHandleLoadCommandSuccessfully() {
        Stanza stanza = createTestStanza(5L, "Haunted mansion", "Ghost investigation");
        when(persistenceService.loadStanzaWithRelationships(5L)).thenReturn(stanza);
        
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
        Stanza stanza = createTestStanza(5L, "Haunted mansion", "Ghost investigation");
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
    
    private Stanza createTestStanza(Long id, String setting, String premise) {
        Persona testPersona = new Persona();
        testPersona.setId(1L);
        testPersona.setName("Test User");
        
        Stanza stanza = new Stanza(testPersona, "test_world");
        stanza.setId(id);
        stanza.setSetting(setting);
        stanza.setPremise(premise);
        stanza.setTone("Horror");
        stanza.setQuickSynopsis("A test synopsis");
        return stanza;
    }
}