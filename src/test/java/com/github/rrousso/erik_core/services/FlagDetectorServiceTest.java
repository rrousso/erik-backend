package com.github.rrousso.erik_core.services;

import com.github.rrousso.erik_core.Entities.Flag;
import com.github.rrousso.erik_core.Entities.ModelType;
import com.github.rrousso.erik_core.Entities.StanzaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    }
    
    @Test
    @DisplayName("Should detect START flag when user says 'let's begin' in NONE status")
    void shouldDetectStartFlag() throws Exception {
    	
        String userInput = "let's begin";
        StanzaStatus status = StanzaStatus.NONE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");
        
        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, status);
        
        assertEquals(Flag.START_STANZA, result);
        
        verify(llmClient, times(1)).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        );
    }
    
    @Test
    @DisplayName("Should return NONE for empty input")
    void shouldReturnNoneForEmptyInput() {

        String userInput = "   ";
        StanzaStatus status = StanzaStatus.ACTIVE;    
        
        Flag result = flagDetector.detect(userInput, status);

        assertEquals(Flag.NONE, result);
        
        verifyNoInteractions(llmClient);
    }
    
    @Test
    @DisplayName("Should return NONE for normal input")
    void shouldReturnNoneForNormalInput() throws Exception {

        String userInput = "I follow the fox";
        StanzaStatus status = StanzaStatus.ACTIVE;    
        
        when(promptBuilder.buildFlagDetectionPrompt())
        .thenReturn("Mock prompt template");
    
	    when(llmClient.call(
	        eq(ModelType.ANALYTICAL),
	        anyString(),
	        anyString()
	    )).thenReturn("NONE");
    
        
        Flag result = flagDetector.detect(userInput, status);

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
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.PAUSED;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("CONTINUE");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("ABANDON");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.PAUSED;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("END");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.NONE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("ABANDON");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.ABANDONED;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("PAUSE");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
        StanzaStatus status = StanzaStatus.COMPLETED;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("START");
        
        Flag result = flagDetector.detect(userInput, status);
        
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
    	
    	when(promptBuilder.buildFlagDetectionPrompt())
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(eq(ModelType.ANALYTICAL), any(), any()))
            .thenThrow(new RuntimeException("API Error"));
        
        Flag result = flagDetector.detect("pause", StanzaStatus.ACTIVE);
        
        assertEquals(Flag.NONE, result); // Should return NONE on error
    }
    
    @Test
    @DisplayName("Should throw NullPointerException for null input")
    void shouldThrowExceptionForNullInput() {
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        // assertThrows verifies an exception is thrown
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect(null, status);
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
        StanzaStatus status = StanzaStatus.ACTIVE;
        
        when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Mock prompt template");

        // LLM returns complete garbage
        when(llmClient.call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            anyString()
        )).thenReturn("GARBAGE_XYZ_NOT_A_FLAG");
        
        Flag result = flagDetector.detect(userInput, status);
        
        assertEquals(Flag.NONE, result);
    }
}
