package com.github.rrousso.erik_core.services.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionContext;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.config.PersonaService;
import com.github.rrousso.erik_core.services.stanza.StanzaPersistenceService;

/**
 * Unit tests for SessionAssemblerService.
 * 
 * This service assembles SessionContext snapshots that answer:
 * "Who is Erik/Narrator right now, and what do they know?"
 * 
 * We test:
 * - VOID mode context assembly
 * - STANZA mode context assembly
 * - Correct data inclusion based on state
 * - Database loading for active stanzas
 * - Fallback to InitializedStanza
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionAssemblerService Tests")
public class SessionAssemblerServiceTest {
    
    @Mock
    private PersonaService configService;
    
    @Mock
    private StanzaPersistenceService persistenceService;
    
    private SessionAssemblerService service;
    
    @BeforeEach
    void setUp() {
        service = new SessionAssemblerService(configService, persistenceService);
        
        // Default mock
        when(configService.getUserPersona()).thenReturn("User: Test User\nPronouns: they/them");
    }
    
    // ========================================
    // VOID MODE TESTS
    // ========================================
    
    @Test
    @DisplayName("Should assemble basic VOID mode context")
    void shouldAssembleBasicVoidModeContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertNotNull(context);
        assertEquals(SessionState.Mode.VOID, context.getMode());
        assertEquals(StanzaStatus.NONE, context.getStanzaStatus());
        assertNotNull(context.getUserPersona());
        assertFalse(context.hasSynopsis());
        assertFalse(context.hasRecentExchanges());
        assertFalse(context.hasInitializedStanza());
        assertFalse(context.hasCompletedStanza());
        assertFalse(context.hasLoadedStanzaMemory());
    }
    
    @Test
    @DisplayName("Should include user persona in VOID context")
    void shouldIncludeUserPersonaInVoidContext() {
        // Arrange
        SessionState state = new SessionState();
        String userPersona = "User: Jane Doe\nPronouns: she/her\nDescription: tall";
        when(configService.getUserPersona()).thenReturn(userPersona);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertEquals(userPersona, context.getUserPersona());
        verify(configService).getUserPersona();
    }
    
    @Test
    @DisplayName("Should include recent exchanges in VOID context")
    void shouldIncludeRecentExchangesInVoidContext() {
        // Arrange
        SessionState state = new SessionState();
        state.getVoidHistory().addUserMessage("Hello Erik");
        state.getVoidHistory().addAssistantMessage("Hi! How can I help?");
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertTrue(context.hasRecentExchanges());
        assertNotNull(context.getRecentExchanges());
    }
    
    @Test
    @DisplayName("Should include paused stanza in VOID context")
    void shouldIncludePausedStanzaInVoidContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);
        
        InitializedStanza initialized = new InitializedStanza();
        initialized.setWorldIdentifier("test_world");
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertEquals(StanzaStatus.PAUSED, context.getStanzaStatus());
        assertTrue(context.hasInitializedStanza());
        assertEquals(initialized, context.getInitializedStanza());
    }
    
    @Test
    @DisplayName("Should include completed stanza in VOID context")
    void shouldIncludeCompletedStanzaInVoidContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.COMPLETED);
        
        InitializedStanza initialized = new InitializedStanza();
        CompletedStanza completed = new CompletedStanza("Synopsis", initialized);
        state.setCompletedStanza(completed);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertTrue(context.hasCompletedStanza());
        assertEquals(completed, context.getCompletedStanza());
    }
    
    @Test
    @DisplayName("Should include loaded stanza memory in VOID context")
    void shouldIncludeLoadedStanzaMemoryInVoidContext() {
        // Arrange
        SessionState state = new SessionState();
        
        Stanza loadedStanza = createMockStanza();
        state.setLoadedStanzaMemory(loadedStanza);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertTrue(context.hasLoadedStanzaMemory());
        assertEquals(loadedStanza, context.getLoadedStanzaMemory());
    }
    
    @Test
    @DisplayName("Should include stanza synopsis in VOID context when paused")
    void shouldIncludeStanzaSynopsisInVoidContextWhenPaused() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);
        
        // CORRECT: Use updateSynopsis() not setSynopsis()
        state.getStanzaHistory().addUserMessage("Some message");
        state.getStanzaHistory().addAssistantMessage("Some response");
        state.getStanzaHistory().updateSynopsis("What happened so far", 1);
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertTrue(context.hasSynopsis());
        assertEquals("What happened so far", context.getSynopsis());
    }
    
    // ========================================
    // STANZA MODE TESTS
    // ========================================
    
    @Test
    @DisplayName("Should assemble basic STANZA mode context")
    void shouldAssembleBasicStanzaModeContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        InitializedStanza initialized = new InitializedStanza();
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        assertNotNull(context);
        assertEquals(SessionState.Mode.STANZA, context.getMode());
        assertEquals(StanzaStatus.ACTIVE, context.getStanzaStatus());
        assertNotNull(context.getUserPersona());
    }
    
    @Test
    @DisplayName("Should include recent exchanges from stanza history in STANZA context")
    void shouldIncludeRecentExchangesFromStanzaHistoryInStanzaContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        state.getStanzaHistory().addUserMessage("I approach the door");
        state.getStanzaHistory().addAssistantMessage("The door creaks open");
        
        InitializedStanza initialized = new InitializedStanza();
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        assertTrue(context.hasRecentExchanges());
    }
    
    @Test
    @DisplayName("Should include synopsis from stanza history in STANZA context")
    void shouldIncludeSynopsisFromStanzaHistoryInStanzaContext() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        // Add messages and create synopsis
        state.getStanzaHistory().addUserMessage("First action");
        state.getStanzaHistory().addAssistantMessage("First response");
        state.getStanzaHistory().addUserMessage("Second action");
        
        // CORRECT: Use updateSynopsis()
        state.getStanzaHistory().updateSynopsis("The story so far", 1);
        
        InitializedStanza initialized = new InitializedStanza();
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        assertTrue(context.hasSynopsis());
        assertEquals("The story so far", context.getSynopsis());
    }
    
    @Test
    @DisplayName("Should load narrator context from database when activeStanzaId is present")
    void shouldLoadNarratorContextFromDatabaseWhenActiveStanzaIdIsPresent() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        state.setActiveStanzaId(1L);
        
        Stanza dbStanza = createMockStanza();
        when(persistenceService.loadStanzaWithRelationships(1L)).thenReturn(dbStanza);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        verify(persistenceService).loadStanzaWithRelationships(1L);
        assertTrue(context.hasNarratorContext());
    }
    
    @Test
    @DisplayName("Should fallback to InitializedStanza when no activeStanzaId")
    void shouldFallbackToInitializedStanzaWhenNoActiveStanzaId() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        state.setActiveStanzaId(null);  // No DB record
        
        InitializedStanza initialized = new InitializedStanza();
        initialized.setWorldIdentifier("fallback_world");
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        verify(persistenceService, never()).loadStanzaWithRelationships(anyLong());
        assertTrue(context.hasInitializedStanza());
        assertEquals("fallback_world", context.getInitializedStanza().getWorldIdentifier());
    }
    
    @Test
    @DisplayName("Should fallback to InitializedStanza when database load fails")
    void shouldFallbackToInitializedStanzaWhenDatabaseLoadFails() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        state.setActiveStanzaId(1L);
        
        when(persistenceService.loadStanzaWithRelationships(1L)).thenReturn(null);
        
        InitializedStanza initialized = new InitializedStanza();
        initialized.setWorldIdentifier("fallback_world");
        state.setInitializedStanza(initialized);
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        verify(persistenceService).loadStanzaWithRelationships(1L);
        assertTrue(context.hasInitializedStanza());
    }
    
    // ========================================
    // EDGE CASE TESTS
    // ========================================
    
    @Test
    @DisplayName("Should handle empty history gracefully")
    void shouldHandleEmptyHistoryGracefully() {
        // Arrange
        SessionState state = new SessionState();
        // No messages in history
        
        // Act
        SessionContext voidContext = service.assembleForVoid(state);
        
        // Assert
        assertFalse(voidContext.hasRecentExchanges());
        assertNotNull(voidContext);
    }
    
    @Test
    @DisplayName("Should handle history with no synopsis")
    void shouldHandleHistoryWithNoSynopsis() {
        // Arrange
        SessionState state = new SessionState();
        // Don't create any synopsis - history starts with empty synopsis by default
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertFalse(context.hasSynopsis());
    }
    
    @Test
    @DisplayName("Should handle history with messages but no synopsis")
    void shouldHandleHistoryWithMessagesButNoSynopsis() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        InitializedStanza initialized = new InitializedStanza();
        state.setInitializedStanza(initialized);
        
        // Add messages but no synopsis
        state.getStanzaHistory().addUserMessage("Test message");
        state.getStanzaHistory().addAssistantMessage("Test response");
        // Don't call updateSynopsis()
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        assertTrue(context.hasRecentExchanges());
        assertFalse(context.hasSynopsis());  // No synopsis created yet
    }
    
    // ========================================
    // VOID VS STANZA SEPARATION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should use void history for VOID mode")
    void shouldUseVoidHistoryForVoidMode() {
        // Arrange
        SessionState state = new SessionState();
        
        // Add different messages to each history
        state.getVoidHistory().addUserMessage("Erik message");
        state.getStanzaHistory().addUserMessage("Narrator message");
        
        // Act
        SessionContext context = service.assembleForVoid(state);
        
        // Assert
        assertTrue(context.hasRecentExchanges());
        assertTrue(context.getRecentExchanges().contains("Erik message"));
        assertFalse(context.getRecentExchanges().contains("Narrator message"));
    }
    
    @Test
    @DisplayName("Should use stanza history for STANZA mode")
    void shouldUseStanzaHistoryForStanzaMode() {
        // Arrange
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        InitializedStanza initialized = new InitializedStanza();
        state.setInitializedStanza(initialized);
        
        // Add different messages to each history
        state.getVoidHistory().addUserMessage("Erik message");
        state.getStanzaHistory().addUserMessage("Narrator message");
        
        // Act
        SessionContext context = service.assembleForStanza(state);
        
        // Assert
        assertTrue(context.hasRecentExchanges());
        assertTrue(context.getRecentExchanges().contains("Narrator message"));
        assertFalse(context.getRecentExchanges().contains("Erik message"));
    }
    
    // ========================================
    // STATUS PRESERVATION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should preserve stanza status in context")
    void shouldPreserveStanzaStatusInContext() {
        // Arrange
        SessionState state = new SessionState();
        
        // Test each status
        for (StanzaStatus status : StanzaStatus.values()) {
            state.setStanzaStatus(status);
            
            // Act
            SessionContext context = service.assembleForVoid(state);
            
            // Assert
            assertEquals(status, context.getStanzaStatus());
        }
    }
    
    // ========================================
    // HELPER METHODS
    // ========================================
    
    private Stanza createMockStanza() {
        Persona persona = new Persona();
        persona.setId(1L);
        persona.setName("Test User");
        
        Stanza stanza = new Stanza(persona, "test_world");
        stanza.setId(1L);
        stanza.setSetting("Test setting");
        stanza.setPremise("Test premise");
        return stanza;
    }
}