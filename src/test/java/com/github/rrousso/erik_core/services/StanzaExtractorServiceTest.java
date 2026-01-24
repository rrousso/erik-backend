package com.github.rrousso.erik_core.services;

import static org.junit.jupiter.api.Assertions.*;
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

import com.github.rrousso.erik_core.entities.ConversationHistory;
import com.github.rrousso.erik_core.entities.ModelType;
import com.github.rrousso.erik_core.entities.StanzaMetadata;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stanza Extractor Service Tests")
public class StanzaExtractorServiceTest {

	@Mock
    private LLMClientService llmClient;
    
	@Mock
	private SystemPromptBuilderService promptBuilder;
    
	private StanzaExtractorService stanzaExtractor;
	
    @BeforeEach
    void setUp(){
    	stanzaExtractor = new StanzaExtractorService(llmClient, promptBuilder);
    }
    
    @Test
    @DisplayName("Should extract stanza from message history")
    void shouldExtractStanzaFromHistory() throws Exception{
    	ConversationHistory history = new ConversationHistory();
    	history.addUserMessage("I want to make a stanza where I am cinderella!");
    	history.addAssistantMessage("Sounds good, do you wanna start from the beginning?");
    	history.addUserMessage("No, right at the ball, when I meet the prince, and make my dress purple. let's start!");
    	
    	when(promptBuilder.buildExtractionPrompt())
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("""
                    {
                    "setting": "Dance ball at the castle",
                    "premise": "The cinderella story",
                    "userRole": "User is cinderella",
                    "tone": "fairy tale, magical",
                    "characters": ["The Prince"],
                    "specialRules": ["The user's dress is purple"]
                  }
                  """);
        
        StanzaMetadata setup = stanzaExtractor.extractFromVoidHistory(history);
        
        assertEquals("Dance ball at the castle", setup.getSetting());
        assertEquals("The cinderella story", setup.getPremise());
        assertEquals(1, setup.getCharacters().size());
        assertTrue(setup.getCharacters().contains("The Prince"));
        assertTrue(setup.getSpecialRules().contains("The user's dress is purple"));
    }
    
    @Test
    @DisplayName("Should extract multiple characters and rules from message history")
    void shouldExtractMultipleFromHistory() throws Exception{
    	ConversationHistory history = new ConversationHistory();
    	history.addUserMessage("I want to make a stanza where I am cinderella!");
    	history.addAssistantMessage("Sounds good, do you wanna start from the beginning?");
    	history.addUserMessage("No, right at the ball, when I meet the prince, and my two stepsisters are there and don't recoginze me, and make my dress purple. let's start!");
    	
    	when(promptBuilder.buildExtractionPrompt())
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("""
                    {
                    "setting": "Dance ball at the castle",
                    "premise": "The cinderella story",
                    "userRole": "User is cinderella",
                    "tone": "fairy tale, magical",
                    "characters": ["The Prince","Stepsister one","Stepsister two"],
                    "specialRules": ["The user's dress is purple","Stepsisters don't recognize the user"]
                  }
                  """);
        
        StanzaMetadata setup = stanzaExtractor.extractFromVoidHistory(history);
        
        assertEquals("Dance ball at the castle", setup.getSetting());
        assertEquals("The cinderella story", setup.getPremise());
        assertEquals(3, setup.getCharacters().size());
        assertEquals(2, setup.getSpecialRules().size());
        assertTrue(setup.getCharacters().contains("The Prince"));
        assertTrue(setup.getSpecialRules().contains("The user's dress is purple"));
    }
    
    @Test
    @DisplayName("Should extract valid fields even from partially malformed JSON")
    void shouldExtractValidFieldsFromPartiallyMalformedJson() throws Exception{
    	ConversationHistory history = new ConversationHistory();
    	history.addUserMessage("I want to make a stanza where I am cinderella!");
    	history.addAssistantMessage("Sounds good, do you wanna start from the beginning?");
    	history.addUserMessage("No, right at the ball, when I meet the prince, and my two stepsisters are there and don't recoginze me, and make my dress purple. let's start!");
    	
    	when(promptBuilder.buildExtractionPrompt())
        .thenReturn("Mock prompt template");
    	
        when(llmClient.call(
                eq(ModelType.ANALYTICAL),
                anyString(),
                anyString()
            )).thenReturn("""
                    {
                    setting: Dance ball at the castle,
                    "premise": "The cinderella story",
                    "userRole": "User is cinderella"
                    "tone": "fairy tale, magical",
                    "characters": ["The Prince","Stepsister one","Stepsister two"],
                    "specialRules": ["The user's dress is purple","Stepsisters don't recognize the user"]
                  
                  """); //missing quotations and a comma
        
        StanzaMetadata setup = stanzaExtractor.extractFromVoidHistory(history);
        
        assertEquals("", setup.getSetting());
        assertEquals("The cinderella story", setup.getPremise());
        assertEquals("User is cinderella", setup.getUserRole());
        assertEquals("fairy tale, magical", setup.getTone());
        assertEquals(3, setup.getCharacters().size());
        assertEquals(2, setup.getSpecialRules().size());

    }
    
    @Test
    @DisplayName("Should propagate LLM exceptions")
    void shouldPropagateExceptions() throws Exception {

        ConversationHistory history = new ConversationHistory();
        history.addUserMessage("Test");

        when(promptBuilder.buildExtractionPrompt())
            .thenReturn("Mock prompt template");

        doThrow(new RuntimeException("API Error"))
            .when(llmClient)
            .call(eq(ModelType.ANALYTICAL), anyString(), anyString());

        assertThrows(RuntimeException.class, () -> {
            stanzaExtractor.extractFromVoidHistory(history);
        });
    }
    
}
