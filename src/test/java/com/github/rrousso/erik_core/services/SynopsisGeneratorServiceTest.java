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

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.services.config.ConfigService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.services.session.SynopsisGeneratorService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Synopsis Generator Service Test")
public class SynopsisGeneratorServiceTest {
	
	@Mock
    private LLMClientService llmClient;
	
	@Mock
	private SystemPromptBuilderService promptBuilder;
    
	@Mock
	private ConfigService configService;
	
	private SynopsisGeneratorService generator;
	
	@BeforeEach
	void setUp(){
		generator = new SynopsisGeneratorService(llmClient, promptBuilder, configService);
	}
	
	@Test
	@DisplayName("Should create an analytical Synopsis")
	void shouldGenerateAnalyticalSynopsis() throws Exception{
		
		ConversationHistory history = new ConversationHistory();
		
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	history.addUserMessage("I do that.");
    	history.addAssistantMessage("And here are the consequences!");
		
    	when(configService.getThresholdSize()).thenReturn(4);
    	
		when(configService.getWindowSize()).thenReturn(2);
		
		when(configService.getUserPersona()).thenReturn("Persona String");
		
    	when(promptBuilder.buildWorldSnapshotPrompt(configService.getUserPersona()))
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("new synopsis");
        
        generator.generateSynopsis(history);
        
        assertEquals("new synopsis", history.getSynopsis());
	}
	
	@Test
	@DisplayName("Should not create an analytical Synopsis for being under the treshold")
	void shouldNotGenerateAnalyticalSynopsisDueToThreshold() throws Exception{
		
		ConversationHistory history = new ConversationHistory();
		history.addUserMessage("I do this.");
		history.addAssistantMessage("And the world reacts to it!");
    	
    	when(configService.getThresholdSize()).thenReturn(4);
        
    	String result = generator.generateSynopsis(history);
        
        assertEquals("", result);
        assertEquals("", history.getSynopsis());
        
        verify(llmClient, never()).call(any(), any(), any());
	}
	
	@Test
	@DisplayName("Should not create an analytical Synopsis for no old messages found")
	void shouldNotGenerateAnalyticalSynopsisDueNoMessages() throws Exception{
		
		ConversationHistory history = new ConversationHistory();
		history.addUserMessage("I do this.");
		history.addAssistantMessage("And the world reacts to it!");
    	
    	when(configService.getThresholdSize()).thenReturn(2);
    	
		when(configService.getWindowSize()).thenReturn(2);
        
		String result = generator.generateSynopsis(history);
        
        assertEquals("", result);
        assertEquals("", history.getSynopsis());
        
        verify(llmClient, never()).call(any(), any(), any());
	}
	
	@Test
	@DisplayName("Should create a quick Synopsis")
	void shouldGenerateQuickSynopsis() throws Exception{
		
		ConversationHistory history = new ConversationHistory();
		
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	history.addUserMessage("I do that.");
    	history.addAssistantMessage("And here are the consequences!");
    	
    	when(configService.getUserPersona()).thenReturn("Persona String");
    	
    	when(promptBuilder.buildQuickSynopsisPrompt(configService.getUserPersona()))
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("This is a very short synopsis for the user");
        
        String quickSynopsis = generator.generateQuickSynopsis(history);
        
        assertEquals("This is a very short synopsis for the user", quickSynopsis);
	}
	
	@Test
	@DisplayName("Should distill changes from Paused conversation")
	void shouldGeneratePauseChanges() throws Exception{
		
		ConversationHistory history = new ConversationHistory();
		
    	history.addUserMessage("I want this to happen.");
    	history.addAssistantMessage("sounds good!");
		
    	when(promptBuilder.buildChangeDistillerPrompt())
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("Make this happen.");
        
        String pausedChanges = generator.generatePauseChanges(history);
        
        assertEquals("Make this happen.", pausedChanges);
	}
	
	@Test
	@DisplayName("Should return true to generate Synopsis")
	void shouldReturnTrueToShouldGenerateSynopsis(){
		
		ConversationHistory history = new ConversationHistory();
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	history.addUserMessage("I do that.");
    	history.addAssistantMessage("And here are the consequences!");
    	
    	when(configService.getThresholdSize()).thenReturn(4);
        
        assertTrue(generator.shouldGenerateSynopsis(history));
	}
	
	@Test
	@DisplayName("Should propagate LLM exceptions during synopsis generation")
	void shouldPropagateLlmExceptionsDuringSynopsis() throws Exception {
	    ConversationHistory history = new ConversationHistory();
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	history.addUserMessage("I do that.");
    	history.addAssistantMessage("And here are the consequences!");
	    
	    when(configService.getThresholdSize()).thenReturn(4);
	    when(configService.getWindowSize()).thenReturn(2);
	    when(configService.getUserPersona()).thenReturn("Persona String");
	    when(promptBuilder.buildWorldSnapshotPrompt(configService.getUserPersona())).thenReturn("prompt");
	    
	    when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
	        .thenThrow(new RuntimeException("API Error"));
	    
	    assertThrows(RuntimeException.class, () -> {
	        generator.generateSynopsis(history);
	    });
	}

}
