package com.github.rrousso.erik_core.services;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Flag Detector Service Tests")
public class FlagDetectorServiceTest {

    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private SystemPromptBuilderService promptBuilder;
    
    private FlagDetectorService flagDetector;
    
    @BeforeEach
    void setUp() {
        flagDetector = new FlagDetectorService(llmClient, promptBuilder);
        
        lenient().when(promptBuilder.buildFlagDetectionPrompt())
        .thenReturn("Mock prompt with {CONVERSATION_CONTEXT} placeholder");
    }
    
    @Test
    @DisplayName("Should return NONE when user describes WHERE to start (without Erik prompting)")
    void shouldReturnNoneWhenDescribingStartLocation() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        state.getVoidHistory().addAssistantMessage("What setting do you want?");
        
        String userInput = "I want to start at the dance scene";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
        
        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("What setting do you want?")
        );
    }
    
    @Test
    @DisplayName("Should return START when user confirms after Erik asks 'Ready?'")
    void shouldReturnStartWhenConfirmingAfterReadyPrompt() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        
        state.getVoidHistory().addAssistantMessage("Perfect setup! Ready to begin?");
        
        String userInput = "yeah";
        
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.START_STANZA, result);

        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("Ready to begin?")
        );
    }
    
    @Test
    @DisplayName("Should return NONE for empty input")
    void shouldReturnNoneForEmptyInput() {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        String userInput = "   ";
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
        verifyNoInteractions(llmClient);
    }
    
    @Test
    @DisplayName("Should return NONE for normal input")
    void shouldReturnNoneForNormalInput() throws Exception {

        String userInput = "I follow the fox";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);  
        
    
	    when(llmClient.call(
	        eq(ModelType.ANALYTICAL),
	        anyString(),
	        anyString()
	    )).thenReturn("NONE");
    
        
        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.NONE, result);
        
        verify(llmClient, times(1)).call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            );
    }
    
    @Test
    @DisplayName("Should detect PAUSE flag in ACTIVE status")
    void shouldDetectPauseFlag() throws Exception {

        String userInput = "((Pause))";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.PAUSE_STANZA, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect CONTINUE flag in PAUSED status")
    void shouldDetectContinueFlag() throws Exception {

        String userInput = "Sounds good Erik, let's continue!";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("CONTINUE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.CONTINUE_STANZA, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect END flag in ACTIVE status")
    void shouldDetectEndFlag() throws Exception {

        String userInput = "((End Stanza))";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.END_STANZA, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect ABANDON flag in ACTIVE status")
    void shouldDetectAbandonFlag() throws Exception {

        String userInput = "((Abandon Stanza))";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("ABANDON");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.ABANDON_STANZA, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect Invalid END flag in PAUSED status")
    void shouldDetectInvalidEndFlag() throws Exception {

        String userInput = "I'm not sure how to end this, Erik";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect Invalid START flag in ACTIVE status")
    void shouldDetectInvalidStartFlag() throws Exception {

        String userInput = "I start building a sand castle";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect Invalid ABANDON flag in NONE status")
    void shouldDetectInvalidAbandonFlag() throws Exception {

        String userInput = "I should abandon this kind of stanza";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("ABANDON");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect Invalid PAUSE flag in ABANDONED status")
    void shouldDetectInvalidPauseFlag() throws Exception {

        String userInput = "I just want to stop thinking about it";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ABANDONED);
        

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should detect Invalid START flag in COMPLETED status")
    void shouldDetectInvalidStartFlagWhileCompleted() throws Exception {

        String userInput = "Let's start a new Stanza!";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.COMPLETED);
        
        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);       

        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should handle LLM error gracefully")
    void shouldHandleLlmError() throws Exception {
    	
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
    	
        when(llmClient.call(eq(ModelType.ANALYTICAL), any(), any()))
            .thenThrow(new RuntimeException("API Error"));
        
        Flag result = flagDetector.detect("pause", state);
        
        assertEquals(Flag.NONE, result); // Should return NONE on error
    }
    
    @Test
    @DisplayName("Should throw NullPointerException for null input")
    void shouldThrowExceptionForNullInput() {
        SessionState state = new SessionState();
        
        // assertThrows verifies an exception is thrown
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect(null, state);
        });
    }

    @Test
    @DisplayName("Should throw NullPointerException for null status")
    void shouldThrowExceptionForNullStatus() {
        String userInput = "pause";
        
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect(userInput, null);
        });
    }
    
    @Test
    @DisplayName("Should return NONE for unrecognized LLM response")
    void shouldReturnNoneForUnrecognizedResponse() throws Exception {
        String userInput = "some input";
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        // LLM returns complete garbage
        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("GARBAGE_XYZ_NOT_A_FLAG");
        
        Flag result = flagDetector.detect(userInput, state);
        
        assertEquals(Flag.NONE, result);
    }
}
