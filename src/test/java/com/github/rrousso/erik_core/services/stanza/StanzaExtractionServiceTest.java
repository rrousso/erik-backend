package com.github.rrousso.erik_core.services.stanza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.config.ExtractionConfig;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.ExtractionPromptBuilder;
import com.github.rrousso.erik_core.services.stanza.appliers.ExtractionApplierRegistry;

/**
 * Tests for StanzaExtractionService
 * 
 * Updated to test:
 * - New processExtraction() method with frequency checking
 * - New forceExtraction() method for beat boundaries
 * - ConversationHistory integration
 * - FactDiscovery system
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class StanzaExtractionServiceTest {
    
    @Mock
    private ExtractionPromptBuilder promptBuilder;
    
    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private ExtractionApplierRegistry applierRegistry;
    
    @Mock
    private ExtractionConfig extractionConfig;
    
    private StanzaExtractionService service;
    
    // Test fixtures
    private Stanza mockStanza;
    private ConversationHistory mockHistory;
    
    @BeforeEach
    void setUp() {
        // Manually construct the service with all mocked dependencies
        service = new StanzaExtractionService(
            promptBuilder,
            llmClient,
            applierRegistry,
            extractionConfig
        );
        
        mockStanza = new Stanza();
        mockStanza.setId(1L);
        mockStanza.setCurrentExchange(3);
        
        mockHistory = new ConversationHistory();
        mockHistory.addUserMessage("I walk into the classroom");
        mockHistory.addAssistantMessage("You step through the doorway. Scott and Stiles glance up from their desks.");
    }
    
    // ========== PROCESS EXTRACTION TESTS ==========
    
    @Test
    @DisplayName("Should process extraction when frequency check passes")
    void shouldProcessExtractionWhenFrequencyCheckPasses() throws Exception {
        // Given
        int exchangeNumber = 3;
        boolean isFirstExchange = false;
        boolean isFinalExchange = false;
        
        when(extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange))
            .thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(3);
        
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), eq(mockHistory), eq(3)))
            .thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString()))
            .thenReturn(mockJsonResponse);
        
        // When
        boolean result = service.processExtraction(mockStanza, mockHistory, exchangeNumber, isFirstExchange, isFinalExchange);
        
        // Then
        assertTrue(result);
        verify(extractionConfig).shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange);
        verify(promptBuilder).buildPrompt(mockStanza, mockHistory, 3);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
        verify(applierRegistry).applyEvents(eq(mockStanza), anyList());
        verify(applierRegistry).applyFactDiscoveries(eq(mockStanza), anyList());
    }
    
    @Test
    @DisplayName("Should skip extraction when frequency check fails")
    void shouldSkipExtractionWhenFrequencyCheckFails() throws Exception {
        // Given
        int exchangeNumber = 2;
        boolean isFirstExchange = false;
        boolean isFinalExchange = false;
        
        when(extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange))
            .thenReturn(false);
        when(extractionConfig.getFrequency()).thenReturn(3);
        
        // When
        boolean result = service.processExtraction(mockStanza, mockHistory, exchangeNumber, isFirstExchange, isFinalExchange);
        
        // Then
        assertFalse(result);
        verify(extractionConfig).shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange);
        verifyNoInteractions(promptBuilder);
        verifyNoInteractions(llmClient);
        verifyNoInteractions(applierRegistry);
    }
    
    @Test
    @DisplayName("Should always extract on first exchange when configured")
    void shouldAlwaysExtractOnFirstExchange() throws Exception {
        // Given
        int exchangeNumber = 1;
        boolean isFirstExchange = true;
        boolean isFinalExchange = false;
        
        when(extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange))
            .thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(3);
        
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        boolean result = service.processExtraction(mockStanza, mockHistory, exchangeNumber, isFirstExchange, isFinalExchange);
        
        // Then
        assertTrue(result);
        verify(extractionConfig).shouldExtract(exchangeNumber, true, false);
    }
    
    @Test
    @DisplayName("Should always extract on final exchange when configured")
    void shouldAlwaysExtractOnFinalExchange() throws Exception {
        // Given
        int exchangeNumber = 10;
        boolean isFirstExchange = false;
        boolean isFinalExchange = true;
        
        when(extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange))
            .thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(3);
        
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        boolean result = service.processExtraction(mockStanza, mockHistory, exchangeNumber, isFirstExchange, isFinalExchange);
        
        // Then
        assertTrue(result);
        verify(extractionConfig).shouldExtract(exchangeNumber, false, true);
    }
    
    // ========== FORCE EXTRACTION TESTS ==========
    
    @Test
    @DisplayName("Should force extraction regardless of frequency config")
    void shouldForceExtractionRegardlessOfFrequency() throws Exception {
        // Given
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(extractionConfig.getFrequency()).thenReturn(3);
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        boolean result = service.forceExtraction(mockStanza, mockHistory);
        
        // Then
        assertTrue(result);
        // shouldExtract should NOT be called - we're forcing
        verify(extractionConfig, never()).shouldExtract(anyInt(), anyBoolean(), anyBoolean());
        verify(promptBuilder).buildPrompt(mockStanza, mockHistory, 3);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should return false when force extraction fails")
    void shouldReturnFalseWhenForceExtractionFails() throws Exception {
        // Given
        when(extractionConfig.getFrequency()).thenReturn(1);
        when(promptBuilder.buildPrompt(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("LLM failure"));
        
        // When
        boolean result = service.forceExtraction(mockStanza, mockHistory);
        
        // Then
        assertFalse(result, "Force extraction should return false when it fails, but returned: " + result);
    }
    
    // ========== EXTRACTION LOGIC TESTS ==========
    
    @Test
    @DisplayName("Should successfully extract and apply all change types with FactDiscovery")
    void shouldExtractAndApplyAllChangeTypesWithFactDiscovery() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        
        String mockPrompt = "Extract changes from this exchange...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), eq(mockHistory), eq(1)))
            .thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString()))
            .thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, mockHistory, 1, false, false);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, mockHistory, 1);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString());
        
        // Verify all appliers were called with non-empty lists
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> list.size() == 2));
        verify(applierRegistry).applyFactDiscoveries(eq(mockStanza), argThat(list -> list.size() == 2));
        verify(applierRegistry).applySecretRevelations(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyTensionChanges(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyCharacterAppearances(eq(mockStanza), argThat(list -> list.size() == 2));
    }
    
    @Test
    @DisplayName("Should only apply event changes when other categories are empty")
    void shouldOnlyApplyEventChangesWhenOtherCategoriesAreEmpty() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, mockHistory, 1, false, false);
        
        // Then
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyFactDiscoveries(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applySecretRevelations(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applyTensionChanges(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applyCharacterAppearances(eq(mockStanza), argThat(List::isEmpty));
    }
    
    @Test
    @DisplayName("Should not call appliers when no changes extracted")
    void shouldNotCallAppliersWhenNoChangesExtracted() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        
        String mockPrompt = "Extract...";
        String mockJsonResponse = createNoChangesJson();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, mockHistory, 1, false, false);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, mockHistory, 1);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
        
        // Verify no appliers were called since no changes
        verify(applierRegistry, never()).applyEvents(any(), any());
        verify(applierRegistry, never()).applyFactDiscoveries(any(), any());
        verify(applierRegistry, never()).applySecretRevelations(any(), any());
        verify(applierRegistry, never()).applyTensionChanges(any(), any());
        verify(applierRegistry, never()).applyCharacterAppearances(any(), any());
    }
    
    @Test
    @DisplayName("Should use ANALYTICAL model type for extraction")
    void shouldUseAnalyticalModelType() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, mockHistory, 1, false, false);
        
        // Then
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should pass correct arguments to prompt builder")
    void shouldPassCorrectArgumentsToPromptBuilder() throws Exception {
        // Given
        ConversationHistory customHistory = new ConversationHistory();
        customHistory.addUserMessage("I attack the werewolf");
        customHistory.addAssistantMessage("You lunge at the creature but it dodges.");
        
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(2);
        
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(mockStanza, customHistory, 2)).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, customHistory, 4, false, false);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, customHistory, 2);
    }
    
    @Test
    @DisplayName("Should call appliers in correct order")
    void shouldCallAppliersInCorrectOrder() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn(mockPrompt);
        when(llmClient.call(any(), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.processExtraction(mockStanza, mockHistory, 1, false, false);
        
        // Then - verify call order
        var inOrder = inOrder(applierRegistry);
        inOrder.verify(applierRegistry).applyEvents(any(), any());
        inOrder.verify(applierRegistry).applyFactDiscoveries(any(), any());
        inOrder.verify(applierRegistry).applySecretRevelations(any(), any());
        inOrder.verify(applierRegistry).applyTensionChanges(any(), any());
        inOrder.verify(applierRegistry).applyCharacterAppearances(any(), any());
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @Test
    @DisplayName("Should not throw exception when LLM call fails")
    void shouldNotThrowWhenLLMCallFails() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn("prompt");
        when(llmClient.call(any(), anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM error"));
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> {
            service.processExtraction(mockStanza, mockHistory, 1, false, false);
        });
        
        // Verify appliers were not called
        verifyNoInteractions(applierRegistry);
    }
    
    @Test
    @DisplayName("Should not throw exception when JSON parsing fails")
    void shouldNotThrowWhenJsonParsingFails() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn("prompt");
        when(llmClient.call(any(), anyString(), anyString()))
            .thenReturn("invalid json {{{");
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> {
            service.processExtraction(mockStanza, mockHistory, 1, false, false);
        });
    }
    
    @Test
    @DisplayName("Should not throw exception when applier fails")
    void shouldNotThrowWhenApplierFails() throws Exception {
        // Given
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(extractionConfig.getFrequency()).thenReturn(1);
        when(promptBuilder.buildPrompt(any(), any(), anyInt())).thenReturn("prompt");
        when(llmClient.call(any(), anyString(), anyString()))
            .thenReturn(createEventsOnlyJson());
        
        doThrow(new RuntimeException("Applier error"))
            .when(applierRegistry).applyEvents(any(), any());
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> {
            service.processExtraction(mockStanza, mockHistory, 1, false, false);
        });
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Create a complete extraction JSON with all change types using new FactDiscovery format
     */
    private String createFullExtractionJsonWithFactDiscovery() {
        return """
            {
              "events": [
                {
                  "description": "User entered the classroom",
                  "significance": "MINOR",
                  "charactersInvolved": ["User"]
                },
                {
                  "description": "Scott and Stiles looked up",
                  "significance": "MINOR",
                  "charactersInvolved": ["Scott McCall", "Stiles Stilinski"]
                }
              ],
              "factDiscoveries": [
                {
                  "tempId": "discovery_1",
                  "statement": "User is a new student",
                  "truthValue": true,
                  "allowedRevealModes": null,
                  "discoveredBy": [
                    {"characterName": "Scott McCall", "howLearned": "OBSERVED"},
                    {"characterName": "Stiles Stilinski", "howLearned": "OBSERVED"}
                  ]
                },
                {
                  "existingFactHash": "a3f8b2c1",
                  "discoveredBy": [
                    {"characterName": "Derek Hale", "howLearned": "TOLD"}
                  ]
                }
              ],
              "secretRevelations": [
                {
                  "secretDescription": "werewolf identity",
                  "characterName": "User",
                  "newState": "SUSPICIOUS",
                  "howRevealed": "Scott McCall's behavior seemed unusual"
                }
              ],
              "tensionChanges": [
                {
                  "tensionDescription": "hiding supernatural nature",
                  "changeType": "PRESSURE_INCREASE",
                  "reason": "New person asking questions"
                }
              ],
              "characterAppearances": [
                {
                  "characterName": "Scott McCall",
                  "changeType": "APPEARED",
                  "context": "Looked up from his desk"
                },
                {
                  "characterName": "Stiles Stilinski",
                  "changeType": "APPEARED",
                  "context": "Was already in the classroom"
                }
              ]
            }
            """;
    }
    
    /**
     * Create JSON with only events (other categories empty)
     */
    private String createEventsOnlyJson() {
        return """
            {
              "events": [
                {
                  "description": "User walked in",
                  "significance": "MINOR",
                  "charactersInvolved": ["User"]
                }
              ],
              "factDiscoveries": [],
              "secretRevelations": [],
              "tensionChanges": [],
              "characterAppearances": []
            }
            """;
    }
    
    /**
     * Create JSON with no changes at all
     */
    private String createNoChangesJson() {
        return """
            {
              "events": [],
              "factDiscoveries": [],
              "secretRevelations": [],
              "tensionChanges": [],
              "characterAppearances": []
            }
            """;
    }
}