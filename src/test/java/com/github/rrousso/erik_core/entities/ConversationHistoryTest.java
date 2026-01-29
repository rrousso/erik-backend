package com.github.rrousso.erik_core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.rrousso.erik_core.domain.models.ConversationHistory;

public class ConversationHistoryTest {
	
    private ConversationHistory history;
    
    @BeforeEach
    void setUp() {
    	history = new ConversationHistory();
    }
	
    @Test
    @DisplayName("Should Add assistant message to history")
    void shouldAddAssistantMessage() {
    	
    	history.addAssistantMessage("Hello there!");
    	
    	ConversationHistory.Message message = history.getAllMessages().get(0);
    	
        assertEquals(1, history.getAllMessages().size());
        assertEquals("assistant", message.getRole());
        assertEquals("Hello there!", message.getContent());
        
    }
    
    @Test
    @DisplayName("Should Add user message to history")
    void shouldAddUserMessage(){
    	
    	history.addUserMessage("I want a stanza about werewolves!");
    	
    	ConversationHistory.Message message = history.getAllMessages().get(0);
    	
        assertEquals(1, history.getAllMessages().size());
        assertEquals("user", message.getRole());
        assertEquals("I want a stanza about werewolves!", message.getContent());
        
    }
    
    @Test
    @DisplayName("Should Add multiple messages to history")
    void shouldAddMultipleMessages(){
    	
    	history.addUserMessage("I want a stanza about werewolves!");
    	history.addAssistantMessage("Sounds great, do you to go the horror route or maybe some other genre?");
    	
    	ConversationHistory.Message user = history.getAllMessages().get(0);
    	ConversationHistory.Message assistant = history.getAllMessages().get(1);
    	
        assertEquals(2, history.getAllMessages().size());
        assertEquals("user", user.getRole());
        assertEquals("I want a stanza about werewolves!", user.getContent());
        assertEquals("assistant", assistant.getRole());
        assertEquals("Sounds great, do you to go the horror route or maybe some other genre?", assistant.getContent());
        
    }
    
    @Test
    @DisplayName("Should get recent exchanges for system prompt")
    void shouldGetRecentExchangesForSystemPrompt(){
    	
    	history.addUserMessage("I want a stanza about werewolves!");
    	history.addAssistantMessage("Sounds great, do you to go the horror route or maybe some other genre?");
    	 
        assertTrue(history.getRecentExchangesForSystemPrompt().contains("USER: I want a stanza about werewolves!"));
        assertTrue(history.getRecentExchangesForSystemPrompt().contains("ASSISTANT: Sounds great, do you to go the horror route or maybe some other genre?"));
        assertTrue(history.getRecentExchangesForSystemPrompt().contains("\n\n"));
        
    }
    
    @Test
    @DisplayName("Should return empty string if no messages available for system prompt")
    void shouldReturnEmptyStringIfNoMessagesForSystemPrompt(){
 	 
        assertEquals("", history.getRecentExchangesForSystemPrompt());
        
    }
    
    @Test
    @DisplayName("Should update Synopis and trim messages")
    void shouldUpdateSynopsisAndTrimMessages(){
    	
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	
    	String newSynopsis = "The user did that";
    	
    	history.updateSynopsis(newSynopsis, 1);
    	
    	assertEquals(1, history.getAllMessages().size());
    	assertEquals("The user did that", history.getSynopsis());
    	
    	ConversationHistory.Message message = history.getAllMessages().get(0);
    	
        assertEquals(1, history.getAllMessages().size());
        assertEquals(1, history.getCurrentHistorySize());
        assertEquals("assistant", message.getRole());
        assertEquals("And the world reacts to it!", message.getContent());
        
    }
    
    @Test
    @DisplayName("Should update synopsis without trimming when window is large enough")
    void shouldUpdateSynopsisWithoutTrimming() {
    	
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	
    	String newSynopsis = "The user did that";
    	
    	history.updateSynopsis(newSynopsis, 2);
    	
    	assertEquals(2, history.getAllMessages().size());
    	assertEquals("The user did that", history.getSynopsis());
    	
    	ConversationHistory.Message user = history.getAllMessages().get(0);
    	ConversationHistory.Message assistant = history.getAllMessages().get(1);
    	
        assertEquals(2, history.getAllMessages().size());
        assertEquals("user", user.getRole());
        assertEquals("I do this.", user.getContent());
        assertEquals("assistant", assistant.getRole());
        assertEquals("And the world reacts to it!", assistant.getContent());
        
    }
    
    @Test
    @DisplayName("Should get past exchanges for synopsis")
    void shouldGetExchangesForSynopsis(){
    	
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	history.addUserMessage("I do that.");
    	history.addAssistantMessage("And here are the consequences!");
    	
    	List<ConversationHistory.Message> oldMessages = history.getExchangesForSynopsis(2);
    	
    	assertEquals(2, oldMessages.size());
    	
    	ConversationHistory.Message user = oldMessages.get(0);
    	ConversationHistory.Message assistant = oldMessages.get(1);
    	
        assertEquals("user", user.getRole());
        assertEquals("I do this.", user.getContent());
        assertEquals("assistant", assistant.getRole());
        assertEquals("And the world reacts to it!", assistant.getContent());
        
    }
    
    @Test
    @DisplayName("Should not get past exchanges for synopsis the messages are not enough messages")
    void shouldNotGetExchangesForSynopsis(){
    	
    	history.addUserMessage("I do this.");
    	
    	List<ConversationHistory.Message> oldMessages = history.getExchangesForSynopsis(2);
    	
    	assertEquals(0, oldMessages.size());
       
    }
    
    @Test
    @DisplayName("Should get the full conversation for extraction")
    void shouldGetConversationForExtraction(){
    	
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	
    	List<ConversationHistory.Message> messages = history.getConversationForExtraction();
    	
    	assertEquals(2, messages.size());
    	
    	ConversationHistory.Message user = messages.get(0);
    	ConversationHistory.Message assistant = messages.get(1);
    	
        assertEquals("user", user.getRole());
        assertEquals("I do this.", user.getContent());
        assertEquals("assistant", assistant.getRole());
        assertEquals("And the world reacts to it!", assistant.getContent());
        
    }
    
    @Test
    @DisplayName("Should clear Current History")
    void shouldClearCurrentHistory(){
    	
    	history.addUserMessage("I do this.");
    	history.addAssistantMessage("And the world reacts to it!");
    	
    	String newSynopsis = "The user did that";
    	
    	history.updateSynopsis(newSynopsis, 1);
    	
    	history.clearHistory();
    	
    	assertEquals(0, history.getAllMessages().size());
    	assertEquals("", history.getSynopsis());
        
    }
    
}
