package com.github.rrousso.erik_core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.entities.Flag;
import com.github.rrousso.erik_core.entities.ModelType;
import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.entities.StanzaSetup;
import com.github.rrousso.erik_core.entities.StanzaStatus;
import com.github.rrousso.erik_core.repositories.PersonaRepository;
import com.github.rrousso.erik_core.repositories.StanzaRecordRepository;


@ExtendWith(MockitoExtension.class)
@DisplayName("Session Flow Service Test")
public class SessionFlowServiceTest {
	
	@Mock
    private LLMClientService llmClient;
	
	@Mock
    private SystemPromptBuilderService promptBuilder;
    
	@Mock
	private StanzaExtractorService stanzaExtractor;
    
	@Mock
	private SynopsisGeneratorService synopsisGenerator;
    
	@Mock
	private FlagDetectorService flagDetector;
	
    private PersonaRepository personaRepository;
    
    private StanzaRecordRepository stanzaRecordRepository;
	
	private SessionFlowService sessionFlowService;
	
	private SessionState state;
	
	@BeforeEach
	void setUp() {
		sessionFlowService = new SessionFlowService(llmClient, promptBuilder, stanzaExtractor, synopsisGenerator, flagDetector, personaRepository, stanzaRecordRepository);
		state = new SessionState();
	}
	
	@Test
	@DisplayName("Should stay in void mode on regular input")
	void shouldStayInVoidModeOnRegularInput() throws Exception{
		
    	when(flagDetector.detect(
    			eq("Hello!"),
    			eq(state)
    			)).thenReturn(Flag.NONE);
    	
    	when(promptBuilder.buildVoidPrompt(eq(state)))
    	.thenReturn("Void Prompt");
    	
        when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Void Prompt"),
                eq("Hello!"))).thenReturn("Hi! How are you?");
        
        String message = sessionFlowService.handleUserInput("Hello!", state);
    
        assertEquals("\n[Erik] Hi! How are you?", message);
        assertTrue(state.isInVoidMode());
	}
	
	@Test
	@DisplayName("Should start stanza in void mode on Start input")
	void shouldStartStanzaInVoidModeOnStartInput() throws Exception{
		
    	when(flagDetector.detect(
    			eq("Let's begin!"),
    			eq(state)
    			)).thenReturn(Flag.START_STANZA);
    	
    	when(promptBuilder.buildVoidPrompt(eq(state)))
    	.thenReturn("Void Prompt");

    	when(promptBuilder.buildStanzaPrompt(
    			eq(state.getCurrentStanza()),
    			eq(state)))
    	.thenReturn("Stanza Prompt");
    	
    	when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Void Prompt"),
                eq("Let's begin!"))).thenReturn("Very well, let's do this!");
    	
        when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Stanza Prompt"),
                anyString())).thenReturn("You enter the dance floor in your purple dress.");
        
        String message = sessionFlowService.handleUserInput("Let's begin!", state);
        
        System.out.println(message);
        
        assertTrue(message.contains("[Erik] Very well, let's do this!"));
        assertTrue(message.contains("[STANZA START]"));
        assertTrue(message.contains("[Opening Narration] You enter the dance floor in your purple dress."));
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());
        assertTrue(state.isInStanzaMode());
	}
	
	@Test
	@DisplayName("Should pause stanza in stanza mode on Pause input")
	void shouldPauseStanzaInStanzaModeOnPauseInput() throws Exception{
		state.enterStanzaMode();
		state.setStanzaStatus(StanzaStatus.ACTIVE);
		
    	when(flagDetector.detect(
    			eq("((Pause, let's do this differently))"),
    			eq(state)
    			)).thenReturn(Flag.PAUSE_STANZA);
    	
    	when(promptBuilder.buildVoidPrompt(
    			eq(state)))
    			.thenReturn("Void Prompt");
    	
    	when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Void Prompt"),
                eq("((Pause, let's do this differently))"))).thenReturn("Sounds, good! should we continue?");
   
        
        String message = sessionFlowService.handleUserInput("((Pause, let's do this differently))", state);
        
        assertTrue(message.contains("[Erik] Sounds, good! should we continue?"));
        assertEquals(StanzaStatus.PAUSED, state.getStanzaStatus());
        assertTrue(state.isInVoidMode());
	}
	
	@Test
	@DisplayName("Should continue stanza in void mode on Continue input")
	void shouldContinueStanzaInVoidModeOnContinueInput() throws Exception{
		
		StanzaSetup setup = new StanzaSetup();
        setup.setSetting("Cinderella story");
		
		state.setStanzaStatus(StanzaStatus.PAUSED);
		state.setCurrentStanza(setup);		
			
    	when(flagDetector.detect(
    			eq("Yeah, let's go back now"),
    			eq(state)
    			)).thenReturn(Flag.CONTINUE_STANZA);
    	
    	when(promptBuilder.buildStanzaPrompt(
    			eq(state.getCurrentStanza()),
    			eq(state)))
    	.thenReturn("Stanza Prompt");

        when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Stanza Prompt"),
                anyString())).thenReturn("Things continue a bit different now.");
        
        String message = sessionFlowService.handleUserInput("Yeah, let's go back now", state);
        
        assertTrue(message.contains("[Narration] Things continue a bit different now."));
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());
        assertTrue(state.isInStanzaMode());
	}
	
	@Test
	@DisplayName("Should end stanza in Stanza mode on End input")
	void shouldEndStanzaInStanzaModeOnEndInput() throws Exception{
		StanzaSetup setup = new StanzaSetup();
        setup.setSetting("Cinderella story");
        
		state.enterStanzaMode();
		state.setStanzaStatus(StanzaStatus.ACTIVE);
		state.setCurrentStanza(setup);	
		
		
    	when(flagDetector.detect(
    			eq("((end stanza))"),
    			eq(state)
    			)).thenReturn(Flag.END_STANZA);
    	
    	when(promptBuilder.buildVoidPrompt(eq(state)))
    	.thenReturn("Void Prompt");
    	
    	when(promptBuilder.buildStanzaPrompt(
    			eq(state.getCurrentStanza()),
    			eq(state)))
    	.thenReturn("Stanza Prompt");
    	
    	when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Stanza Prompt"),
                anyString())).thenReturn("You and the prince kiss and leave into the sunset.");
    	
    	when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Void Prompt"),
                anyString())).thenReturn("That was a nice ending, what do you feel about it?");
        
        String message = sessionFlowService.handleUserInput("((end stanza))", state);
                
        assertTrue(message.contains("[Narration - Closing] You and the prince kiss and leave into the sunset."));
        assertTrue(message.contains("[STANZA END]"));
        assertTrue(message.contains("[System] Here's the quick synopsis:"));
        assertTrue(message.contains("[Erik] That was a nice ending, what do you feel about it?"));
        assertEquals(StanzaStatus.COMPLETED, state.getStanzaStatus());
        assertTrue(state.isInVoidMode());
	}
	
	@Test
	@DisplayName("Should abandon stanza in Stanza mode on Abandon input")
	void shouldAbandonStanzaInStanzaModeOnAbandonInput() throws Exception{
		StanzaSetup setup = new StanzaSetup();
        setup.setSetting("Cinderella story");
        
		state.enterStanzaMode();
		state.setStanzaStatus(StanzaStatus.ACTIVE);
		state.setCurrentStanza(setup);	
		
		
    	when(flagDetector.detect(
    			eq("((abandon stanza))"),
    			eq(state)
    			)).thenReturn(Flag.ABANDON_STANZA);
    	
    	when(promptBuilder.buildVoidPrompt(eq(state)))
    	.thenReturn("Void Prompt");;
    	
    	when(llmClient.call(
                eq(ModelType.NARRATIVE),
                eq("Void Prompt"),
                anyString())).thenReturn("Do you want to start a new one?");
    	
        String message = sessionFlowService.handleUserInput("((abandon stanza))", state);
                
        assertTrue(message.contains("[Erik] Do you want to start a new one?"));
        assertEquals(StanzaStatus.NONE, state.getStanzaStatus());
        assertTrue(state.isInVoidMode());
        assertNull(state.getCurrentStanza());
        assertNull(state.getCompletedStanza());
	}
	
	@Test
	@DisplayName("Should not start stanza in Void mode on Start input after and Completed status")
	void shouldNotStartStanzaInVoidModeOnStartInputAndCompletedStatus() throws Exception{
		state.setStanzaStatus(StanzaStatus.COMPLETED);
		
    	when(flagDetector.detect(
    			eq("Let's start a new stanza!"),
    			eq(state)
    			)).thenReturn(Flag.START_STANZA);
    	
        String message = sessionFlowService.handleUserInput("Let's start a new stanza!", state);
                
        assertTrue(message.contains("[System] You've already completed a stanza this session."));
        assertEquals(StanzaStatus.COMPLETED, state.getStanzaStatus());
        assertTrue(state.isInVoidMode());
	}
	
	@Test
	@DisplayName("Should handle LLM exceptions gracefully during input processing")
	void shouldHandleLlmExceptionsGracefullyDuringInputProcessing() throws Exception{
    	when(flagDetector.detect(
    			eq("Hello!"),
    			eq(state)
    			)).thenReturn(Flag.NONE);
    	
    	when(promptBuilder.buildVoidPrompt(eq(state)))
    	.thenReturn("Void Prompt");
    	
        doThrow(new RuntimeException("API Error"))
        .when(llmClient)
        .call(eq(ModelType.NARRATIVE), eq("Void Prompt"), eq("Hello!"));
        
        String message = sessionFlowService.handleUserInput("Hello!", state);
        
        assertTrue(message.contains("error"));
        assertTrue(state.isInVoidMode());

	}

}
