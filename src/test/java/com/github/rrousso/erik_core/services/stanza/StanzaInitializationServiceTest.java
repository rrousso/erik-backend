package com.github.rrousso.erik_core.services.stanza;

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
import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_core.dto.initialization.StanzaCharacter;
import com.github.rrousso.erik_core.dto.initialization.UserCharacter;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.config.PersonaService;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.PromptLoaderService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stanza Initialization Service Test")
public class StanzaInitializationServiceTest {
    
    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private PromptLoaderService promptLoader;
    
    @Mock
    private PersonaService configService;
    
    @Mock
    private Stanza mockLoadedStanza;
    
    private StanzaInitializationService service;
    
    private ConversationHistory planningHistory;
    
    @BeforeEach
    void setUp() {
        service = new StanzaInitializationService(llmClient, promptLoader, configService);
        
        // Setup planning conversation
        planningHistory = new ConversationHistory();
        planningHistory.addUserMessage("I want to play in the Teen Wolf universe");
        planningHistory.addAssistantMessage("Great! Tell me about your character.");
        planningHistory.addUserMessage("I'm a new student at Beacon Hills High");
    }
    
    // ========== SUCCESSFUL INITIALIZATION TESTS ==========
    
    @Test
    @DisplayName("Should successfully initialize stanza from planning conversation")
    void shouldInitializeStanzaFromPlanning() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza from planning...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        assertNotNull(result);
        assertEquals("teen_wolf", result.getWorldIdentifier());
        assertNotNull(result.getUserCharacter());
        assertEquals("New student at Beacon Hills High School", result.getUserCharacter().getPublicRole());
        assertEquals(2, result.getExplicitCharacters().size());
        assertEquals(1, result.getLikelyCharacters().size());
        assertEquals(1, result.getBackgroundCharacters().size());
        assertEquals(1, result.getInitialTensions().size());
        
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
        verify(promptLoader).load("architect/initialization_prompt.txt");
        verify(configService).getUserPersona();
    }
    
    @Test
    @DisplayName("Should include loaded stanza context when continuing a story")
    void shouldIncludeLoadedStanzaInPlanning() throws Exception {
        // Given
        when(mockLoadedStanza.getWorldIdentifier()).thenReturn("teen_wolf");
        when(mockLoadedStanza.getSetting()).thenReturn("Beacon Hills High");
        when(mockLoadedStanza.getPremise()).thenReturn("New werewolf in town");
        when(mockLoadedStanza.getTone()).thenReturn("Supernatural drama");
        
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), contains("LOADED STANZA"))).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, mockLoadedStanza);
        
        // Then
        assertNotNull(result);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), contains("LOADED STANZA"));
    }
    
    @Test
    @DisplayName("Should handle stanza with clarifications needed")
    void shouldHandleStanzaWithClarifications() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponseWithClarifications();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        assertNotNull(result);
        assertTrue(result.needsClarification());
        assertEquals(2, result.getClarificationsNeeded().size());
        assertTrue(result.getClarificationsNeeded().contains("What is your character's supernatural status?"));
    }
    
    @Test
    @DisplayName("Should properly parse complex character knowledge boundaries")
    void shouldParseCharacterKnowledgeBoundaries() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        StanzaCharacter scottMcCall = result.getExplicitCharacters().stream()
            .filter(c -> c.getName().equals("Scott McCall"))
            .findFirst()
            .orElse(null);
        
        assertNotNull(scottMcCall);
        assertEquals(2, scottMcCall.getCurrentKnowledge().size());
        assertTrue(scottMcCall.getCurrentKnowledge().contains("User is a new student"));
        assertEquals(1, scottMcCall.getDoesNotKnow().size());
        assertTrue(scottMcCall.getDoesNotKnow().contains("User's supernatural status"));
    }
    
    @Test
    @DisplayName("Should parse world context properly")
    void shouldParseWorldContext() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        assertNotNull(result.getWorldContext());
        assertEquals("Modern day Beacon Hills", result.getWorldContext().getTimeContext());
        assertEquals("Werewolves control the supernatural balance", result.getWorldContext().getCurrentWorldState());
        assertEquals(2, result.getWorldContext().getSupernaturalRules().size());
        assertEquals(1, result.getWorldContext().getRelevantLocations().size());
    }
    
    // ========== VALIDATION TESTS ==========
    
    @Test
    @DisplayName("Should validate valid initialized stanza")
    void shouldValidateValidStanza() {
        // Given
        InitializedStanza stanza = new InitializedStanza();
        stanza.setWorldIdentifier("teen_wolf");
        
        UserCharacter user = new UserCharacter();
        user.setPublicRole("Student");
        stanza.setUserCharacter(user);
        
        // When
        boolean result = service.isValid(stanza);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    @DisplayName("Should reject null stanza")
    void shouldRejectNullStanza() {
        // When
        boolean result = service.isValid(null);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should reject stanza without user character")
    void shouldRejectStanzaWithoutUserCharacter() {
        // Given
        InitializedStanza stanza = new InitializedStanza();
        stanza.setWorldIdentifier("teen_wolf");
        stanza.setUserCharacter(null);
        
        // When
        boolean result = service.isValid(stanza);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should reject stanza without world identifier")
    void shouldRejectStanzaWithoutWorldIdentifier() {
        // Given
        InitializedStanza stanza = new InitializedStanza();
        stanza.setWorldIdentifier(null);
        
        UserCharacter user = new UserCharacter();
        user.setPublicRole("Student");
        stanza.setUserCharacter(user);
        
        // When
        boolean result = service.isValid(stanza);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should reject stanza with empty world identifier")
    void shouldRejectStanzaWithEmptyWorldIdentifier() {
        // Given
        InitializedStanza stanza = new InitializedStanza();
        stanza.setWorldIdentifier("");
        
        UserCharacter user = new UserCharacter();
        user.setPublicRole("Student");
        stanza.setUserCharacter(user);
        
        // When
        boolean result = service.isValid(stanza);
        
        // Then
        assertFalse(result);
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @Test
    @DisplayName("Should propagate LLM client exceptions")
    void shouldPropagateLlmClientExceptions() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM API Error"));
        
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            service.initializeFromPlanning(planningHistory, null);
        });
    }
    
    @Test
    @DisplayName("Should propagate prompt loader exceptions")
    void shouldPropagatePromptLoaderExceptions() throws Exception {
        // Given
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt"))
            .thenThrow(new RuntimeException("Prompt file not found"));
        
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            service.initializeFromPlanning(planningHistory, null);
        });
    }
    
    @Test
    @DisplayName("Should handle invalid JSON response")
    void shouldHandleInvalidJsonResponse() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String invalidJson = "This is not valid JSON { broken";
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(invalidJson);
        
        // When/Then
        assertThrows(Exception.class, () -> {
            service.initializeFromPlanning(planningHistory, null);
        });
    }
    
    @Test
    @DisplayName("Should handle JSON with extra markdown formatting")
    void shouldHandleJsonWithMarkdown() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String jsonWithMarkdown = "```json\n" + createMockJsonResponse() + "\n```";
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(jsonWithMarkdown);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        assertNotNull(result);
        assertEquals("teen_wolf", result.getWorldIdentifier());
    }
    
    // ========== EDGE CASES ==========
    
    @Test
    @DisplayName("Should handle empty planning conversation")
    void shouldHandleEmptyPlanningConversation() throws Exception {
        // Given
        ConversationHistory emptyHistory = new ConversationHistory();
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(emptyHistory, null);
        
        // Then
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("Should handle stanza with no explicit characters")
    void shouldHandleStanzaWithNoExplicitCharacters() throws Exception {
        // Given
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponseMinimal();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(planningHistory, null);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getExplicitCharacters().isEmpty());
        assertEquals("original", result.getWorldIdentifier());
    }
    
    @Test
    @DisplayName("Should handle very long planning conversation")
    void shouldHandleVeryLongPlanningConversation() throws Exception {
        // Given
        ConversationHistory longHistory = new ConversationHistory();
        for (int i = 0; i < 50; i++) {
            longHistory.addUserMessage("Message " + i);
            longHistory.addAssistantMessage("Response " + i);
        }
        
        String mockPrompt = "Initialize stanza...";
        String mockJsonResponse = createMockJsonResponse();
        
        when(configService.getUserPersona()).thenReturn("Test Persona");
        when(promptLoader.load("architect/initialization_prompt.txt")).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        InitializedStanza result = service.initializeFromPlanning(longHistory, null);
        
        // Then
        assertNotNull(result);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), contains("Message 49"));
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Create a mock JSON response for a typical Teen Wolf stanza initialization
     */
    private String createMockJsonResponse() {
        return """
            {
              "worldIdentifier": "teen_wolf",
              "userCharacter": {
                "publicRole": "New student at Beacon Hills High School",
                "privateBackstory": "Recently moved to Beacon Hills, unaware of supernatural happenings",
                "currentLocation": "Beacon Hills High School hallway",
                "currentGoals": ["Make friends", "Figure out the weird vibes in this town"],
                "knownFacts": ["School has a lacrosse team", "Town seems quiet"],
                "publiclyVisibleTraits": ["Nervous", "Curious", "Friendly"]
              },
              "explicitCharacters": [
                {
                  "name": "Scott McCall",
                  "canonRole": "True Alpha werewolf, leader of the pack",
                  "presentInFirstScene": true,
                  "currentKnowledge": ["User is a new student", "User seems friendly"],
                  "doesNotKnow": ["User's supernatural status"],
                  "currentEmotionalState": "Welcoming but cautious",
                  "currentMotivations": ["Protect the pack", "Keep supernatural secret"],
                  "relationshipToUser": "Just met",
                  "whyIncluded": "Alpha werewolf, central figure in Beacon Hills"
                },
                {
                  "name": "Stiles Stilinski",
                  "canonRole": "Scott's best friend, human with supernatural knowledge",
                  "presentInFirstScene": true,
                  "currentKnowledge": ["User is new", "User enrolled today"],
                  "doesNotKnow": ["User's background", "Why user moved here"],
                  "currentEmotionalState": "Curious and talkative",
                  "currentMotivations": ["Help Scott", "Investigate newcomers"],
                  "relationshipToUser": "Just met",
                  "whyIncluded": "Scott's sidekick, information gatherer"
                }
              ],
              "likelyCharacters": [
                {
                  "name": "Lydia Martin",
                  "canonRole": "Banshee, popular student",
                  "presentInFirstScene": false,
                  "currentKnowledge": ["New student enrolled"],
                  "doesNotKnow": ["User's name", "User's background"],
                  "currentEmotionalState": "Indifferent",
                  "currentMotivations": ["Maintain social status"],
                  "relationshipToUser": "Unaware of user",
                  "whyIncluded": "Key pack member, likely to meet soon"
                }
              ],
              "backgroundCharacters": [
                {
                  "name": "Random Students",
                  "role": "Background students at Beacon Hills High"
                }
              ],
              "initialTensions": [
                {
                  "name": "Supernatural Secret",
                  "description": "Scott and pack must keep supernatural world hidden from new student",
                  "currentPressure": 5,
                  "affectedCharacters": ["Scott McCall", "Stiles Stilinski"]
                }
              ],
              "worldContext": {
                "timeContext": "Modern day Beacon Hills",
                "currentWorldState": "Werewolves control the supernatural balance",
                "supernaturalRules": [
                  "Werewolves exist and live among humans",
                  "The supernatural must be kept secret from uninitiated humans"
                ],
                "relevantLocations": [
                  {
                    "name": "Beacon Hills High School",
                    "description": "Main high school where most of the pack attends",
                    "whoMightBeThere": ["Scott", "Stiles", "Lydia", "Students"]
                  }
                ]
              },
              "clarificationsNeeded": []
            }
            """;
    }
    
    /**
     * Create a mock JSON response with clarifications needed
     */
    private String createMockJsonResponseWithClarifications() {
        return """
            {
              "worldIdentifier": "teen_wolf",
              "userCharacter": {
                "publicRole": "New student",
                "privateBackstory": "Unknown",
                "currentLocation": "Beacon Hills High",
                "currentGoals": [],
                "knownFacts": [],
                "publiclyVisibleTraits": []
              },
              "explicitCharacters": [],
              "likelyCharacters": [],
              "backgroundCharacters": [],
              "initialTensions": [],
              "worldContext": {
                "timeContext": "Modern day",
                "currentWorldState": "Unknown",
                "supernaturalRules": [],
                "relevantLocations": []
              },
              "clarificationsNeeded": [
                "What is your character's supernatural status?",
                "What brings your character to Beacon Hills?"
              ]
            }
            """;
    }
    
    /**
     * Create a minimal mock JSON response (original world, minimal data)
     */
    private String createMockJsonResponseMinimal() {
        return """
            {
              "worldIdentifier": "original",
              "userCharacter": {
                "publicRole": "Adventurer",
                "privateBackstory": "Mysterious past",
                "currentLocation": "Unknown",
                "currentGoals": ["Explore"],
                "knownFacts": [],
                "publiclyVisibleTraits": []
              },
              "explicitCharacters": [],
              "likelyCharacters": [],
              "backgroundCharacters": [],
              "initialTensions": [],
              "worldContext": {
                "timeContext": "Fantasy realm",
                "currentWorldState": "Peaceful",
                "supernaturalRules": [],
                "relevantLocations": []
              },
              "clarificationsNeeded": []
            }
            """;
    }
}
