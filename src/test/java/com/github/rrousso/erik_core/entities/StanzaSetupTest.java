package com.github.rrousso.erik_core.entities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stanza Setup Entity test")
public class StanzaSetupTest {
	
	private StanzaMetadata stanzaMetadata;
	
    @BeforeEach
    void setUp(){
    	stanzaMetadata = new StanzaMetadata();
    }
    
    @Test
    @DisplayName("Should parse To a String for Context to the Narrator")
    void shouldParseToNarratorContext(){
    	stanzaMetadata.setSetting("Dance ball at the castle");
    	stanzaMetadata.setPremise("The cinderella story");
    	stanzaMetadata.setTone("fairy tale, magical");
    	stanzaMetadata.setUserRole("User is cinderella");
    	
    	List<String> characters = new ArrayList<String>();
    	characters.add("The Prince");
    	stanzaMetadata.setCharacters(characters);
    	
    	List<String> rules = new ArrayList<String>();
    	rules.add("The user's dress is purple");
    	rules.add("Stepsisters don't recognize the user");
    	stanzaMetadata.setSpecialRules(rules);
    	
    	String context = stanzaMetadata.toNarratorContext();
    	
        assertTrue(context.contains("Setting: Dance ball at the castle"));
        assertTrue(context.contains("Premise: The cinderella story"));
        assertTrue(context.contains("User's Role: User is cinderella"));
        assertTrue(context.contains("Tone/Genre: fairy tale, magical"));
        assertTrue(context.contains("Characters Present:"));
        assertTrue(context.contains("- The Prince"));
        assertTrue(context.contains("Special Rules:"));
        assertTrue(context.contains("- The user's dress is purple"));
    }
    
    @Test
    @DisplayName("Should handle empty fields gracefully")
    void shouldHandleEmptyFields() {
        StanzaMetadata stanzaMetadata = new StanzaMetadata();
        stanzaMetadata.setSetting("Beach");
        
        String context = stanzaMetadata.toNarratorContext();
        
        assertTrue(context.contains("CURRENT STANZA SETUP:"));
        assertTrue(context.contains("Setting: Beach"));
        
        // Empty fields should not appear
        assertFalse(context.contains("Premise:"));
        assertFalse(context.contains("User's Role:"));
        assertFalse(context.contains("Characters Present:"));
    }
    
    
    @Test
    @DisplayName("Should handle completely empty setup")
    void shouldHandleCompletelyEmptySetup() {
        StanzaMetadata emptySetup = new StanzaMetadata();
        
        String context = emptySetup.toNarratorContext();
        
        assertTrue(context.contains("CURRENT STANZA SETUP:"));
        
        assertFalse(context.contains("Setting:"));
        assertFalse(context.contains("Premise:"));
        assertFalse(context.contains("Characters Present:"));
    }
}
