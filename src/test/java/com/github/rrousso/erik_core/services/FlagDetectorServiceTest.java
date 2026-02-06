package com.github.rrousso.erik_core.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.domain.enums.Flag;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionState;
import com.github.rrousso.erik_core.services.llm.FlagDetectorService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.SystemPromptBuilderService;

/**
 * Comprehensive tests for FlagDetectorService.
 * 
 * The FlagDetectorService is critical for routing user input to the correct strategy.
 * It uses conversation context to distinguish between:
 * - Descriptive "start" (planning) vs Command "start" (confirm to begin)
 * - Various pause/continue/end/abandon flags
 * - Context-aware detection based on stanza status
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlagDetectorService Tests - Comprehensive")
public class FlagDetectorServiceTest {

    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private SystemPromptBuilderService promptBuilder;
    
    private FlagDetectorService flagDetector;
    
    @BeforeEach
    void setUp() {
        flagDetector = new FlagDetectorService(llmClient, promptBuilder);
        
        // Mock the prompt builder to return a valid template
        // Using lenient() because some tests (null/blank input validation) return early and don't use this stub
        lenient().when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Detect flag from: {USER_INPUT}\nContext: {CONVERSATION_CONTEXT}\nStatus: {STATUS}\nAvailable: {AVAILABLE_FLAGS}");
    }
    
    // ========================================
    // INPUT VALIDATION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should throw NullPointerException when userInput is null")
    void shouldThrowExceptionWhenUserInputIsNull() {
        SessionState state = new SessionState();
        
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect(null, state);
        });
        
        verifyNoInteractions(llmClient);
    }
    
    @Test
    @DisplayName("Should throw NullPointerException when state is null")
    void shouldThrowExceptionWhenStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect("test input", null);
        });
        
        verifyNoInteractions(llmClient);
    }
    
    @Test
    @DisplayName("Should return NONE when userInput is blank")
    void shouldReturnNoneWhenInputIsBlank() {
        SessionState state = new SessionState();
        
        Flag result = flagDetector.detect("   ", state);
        
        assertEquals(Flag.NONE, result);
        verifyNoInteractions(llmClient);
    }
    
    @Test
    @DisplayName("Should return NONE when userInput is empty")
    void shouldReturnNoneWhenInputIsEmpty() {
        SessionState state = new SessionState();
        
        Flag result = flagDetector.detect("", state);
        
        assertEquals(Flag.NONE, result);
        verifyNoInteractions(llmClient);
    }
    
    // ========================================
    // START FLAG DETECTION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return START when user confirms after Erik asks 'Ready to begin?'")
    void shouldReturnStartWhenConfirmingAfterReadyPrompt() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("Perfect setup! Ready to begin?");
        
        String userInput = "Yes!";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.START_STANZA, result);
        
        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("Perfect setup! Ready to begin?")
        );
    }
    
    @Test
    @DisplayName("Should return NONE when user describes WHERE to start (planning phase)")
    void shouldReturnNoneWhenDescribingStartLocation() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("What setting do you want?");
        
        String userInput = "I want to start at the dance scene";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
    }
    
    @Test
    @DisplayName("Should return NONE when trying to START but stanza already completed")
    void shouldReturnNoneWhenStartingButStanzaCompleted() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.COMPLETED);
        
        String userInput = "Let's start";
        
        // Even if LLM returns START, we override to NONE for completed stanzas
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, state);
        
        // The service should prevent starting when already completed
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // PAUSE FLAG DETECTION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return PAUSE when user says ((pause)) during active stanza")
    void shouldReturnPauseWhenUserSaysPauseDuringActiveStanza() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "((pause))";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.PAUSE_STANZA, result);
    }
    
    @Test
    @DisplayName("Should return PAUSE with natural language pause request")
    void shouldReturnPauseWithNaturalLanguage() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "Hold on, let's pause here";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.PAUSE_STANZA, result);
    }
    
    @Test
    @DisplayName("Should return NONE when trying to PAUSE but not in active stanza")
    void shouldReturnNoneWhenPausingButNotActive() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        String userInput = "pause";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        // Can't pause if not active
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // CONTINUE FLAG DETECTION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return CONTINUE when user wants to resume paused stanza")
    void shouldReturnContinueWhenResumingPausedStanza() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);
        
        String userInput = "Let's continue";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("CONTINUE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.CONTINUE_STANZA, result);
    }
    
    @Test
    @DisplayName("Should return NONE when trying to CONTINUE but not paused")
    void shouldReturnNoneWhenContinuingButNotPaused() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "continue";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("CONTINUE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        // Can't continue if not paused
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // END FLAG DETECTION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return END when user says ((end stanza))")
    void shouldReturnEndWhenUserSaysEndStanza() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "((end stanza))";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.END_STANZA, result);
    }
    
    @Test
    @DisplayName("Should return END with natural language end request")
    void shouldReturnEndWithNaturalLanguage() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "I think this is a good place to end";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.END_STANZA, result);
    }
    
    @Test
    @DisplayName("Should return NONE when trying to END but not in active stanza")
    void shouldReturnNoneWhenEndingButNotActive() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        String userInput = "end";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, state);
        
        // Can't end if not active
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // ABANDON FLAG DETECTION TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return ABANDON when user wants to abandon stanza")
    void shouldReturnAbandonWhenUserWantsToAbandon() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "((abandon))";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("ABANDON");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.ABANDON_STANZA, result);
    }
    
    // ========================================
    // NONE FLAG TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return NONE for regular conversation")
    void shouldReturnNoneForRegularConversation() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        String userInput = "I want to create a vampire story";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
    }
    
    @Test
    @DisplayName("Should return NONE for narrative action during stanza")
    void shouldReturnNoneForNarrativeAction() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "I walk towards the mysterious door";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // ERROR HANDLING TESTS
    // ========================================
    
    @Test
    @DisplayName("Should return NONE when LLM throws exception")
    void shouldReturnNoneWhenLlmThrowsException() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenThrow(new RuntimeException("API Error"));
        
        Flag result = flagDetector.detect("pause", state);
        
        assertEquals(Flag.NONE, result);
    }
    
    @Test
    @DisplayName("Should return NONE for unrecognized LLM response")
    void shouldReturnNoneForUnrecognizedResponse() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        // LLM returns garbage
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("GARBAGE_XYZ_NOT_A_FLAG");
        
        Flag result = flagDetector.detect("some input", state);
        
        assertEquals(Flag.NONE, result);
    }
    
    @Test
    @DisplayName("Should return NONE for null LLM response")
    void shouldReturnNoneForNullLlmResponse() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(null);
        
        Flag result = flagDetector.detect("test", state);
        
        assertEquals(Flag.NONE, result);
    }
    
    @Test
    @DisplayName("Should return NONE for empty LLM response")
    void shouldReturnNoneForEmptyLlmResponse() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("");
        
        Flag result = flagDetector.detect("test", state);
        
        assertEquals(Flag.NONE, result);
    }
    
    // ========================================
    // CONVERSATION CONTEXT TESTS
    // ========================================
    
    @Test
    @DisplayName("Should include conversation context in LLM call")
    void shouldIncludeConversationContextInLlmCall() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("What kind of story?");
        state.getVoidHistory().addUserMessage("A vampire romance");
        state.getVoidHistory().addAssistantMessage("Great! Ready to begin?");
        
        String userInput = "Yes!";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");
        
        flagDetector.detect(userInput, state);
        
        // Verify the context includes Erik's last message
        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("Ready to begin?")
        );
    }
    
    @Test
    @DisplayName("Should handle empty conversation history gracefully")
    void shouldHandleEmptyConversationHistory() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        // No messages in history
        
        String userInput = "Hello";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
        
        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    // ========================================
    // CASE SENSITIVITY TESTS
    // ========================================
    
    @Test
    @DisplayName("Should handle lowercase flag from LLM")
    void shouldHandleLowercaseFlag() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("pause");
        
        Flag result = flagDetector.detect("pause", state);
        
        assertEquals(Flag.PAUSE_STANZA, result);
    }
    
    @Test
    @DisplayName("Should handle mixed case flag from LLM")
    void shouldHandleMixedCaseFlag() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PaUsE");
        
        Flag result = flagDetector.detect("pause", state);
        
        assertEquals(Flag.PAUSE_STANZA, result);
    }
    
    // ========================================
    // MULTIPLE CALLS TESTS
    // ========================================
    
    @Test
    @DisplayName("Should handle multiple consecutive detections correctly")
    void shouldHandleMultipleConsecutiveDetections() throws Exception {
        SessionState state = new SessionState();
        
        // First detection - NONE
        state.setStanzaStatus(StanzaStatus.NONE);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        assertEquals(Flag.NONE, flagDetector.detect("Hello", state));
        
        // Second detection - START
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");
        assertEquals(Flag.START_STANZA, flagDetector.detect("Yes, start", state));
        
        // Third detection - PAUSE
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");
        assertEquals(Flag.PAUSE_STANZA, flagDetector.detect("pause", state));
        
        // Fourth detection - CONTINUE
        state.setStanzaStatus(StanzaStatus.PAUSED);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("CONTINUE");
        assertEquals(Flag.CONTINUE_STANZA, flagDetector.detect("continue", state));
        
        // Verify all LLM calls happened
        verify(llmClient, times(4)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
}