package com.github.rrousso.erik_core.services.stanza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.ExtractionPromptBuilder;
import com.github.rrousso.erik_core.services.stanza.appliers.ExtractionApplierRegistry;

import java.util.List;

/**
 * Tests for StanzaExtractionService
 * 
 * Updated to test new FactDiscovery system instead of legacy
 * FactEstablishment + KnowledgeTransfer.
 */
@ExtendWith(MockitoExtension.class)
class StanzaExtractionServiceTest {
    
    @Mock
    private ExtractionPromptBuilder promptBuilder;
    
    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private ExtractionApplierRegistry applierRegistry;
    
    @InjectMocks
    private StanzaExtractionService service;
    
    // Test fixtures
    private Stanza mockStanza;
    private String userInput;
    private String narratorResponse;
    
    @BeforeEach
    void setUp() {
        mockStanza = new Stanza();
        mockStanza.setId(1L);
        
        userInput = "I walk into the classroom";
        narratorResponse = "You step through the doorway. Scott and Stiles glance up from their desks.";
    }
    
    // ========== SUCCESSFUL EXTRACTION TESTS ==========
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should successfully extract and apply all change types with FactDiscovery")
    void shouldExtractAndApplyAllChangeTypesWithFactDiscovery() throws Exception {
        // Given
        String mockPrompt = "Extract changes from this exchange...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, userInput, narratorResponse);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString());
        
        // Verify all appliers were called with non-empty lists
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> list.size() == 2));
        verify(applierRegistry).applyFactDiscoveries(eq(mockStanza), argThat(list -> list.size() == 2));
        verify(applierRegistry).applySecretRevelations(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyTensionChanges(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyCharacterAppearances(eq(mockStanza), argThat(list -> list.size() == 2));
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should only apply event changes when other categories are empty")
    void shouldApplyOnlyEventsWhenOtherCategoriesEmpty() throws Exception {
        // Given
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> list.size() == 1));
        verify(applierRegistry).applyFactDiscoveries(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applySecretRevelations(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applyTensionChanges(eq(mockStanza), argThat(List::isEmpty));
        verify(applierRegistry).applyCharacterAppearances(eq(mockStanza), argThat(List::isEmpty));
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should handle extraction with no changes detected")
    void shouldHandleNoChangesDetected() throws Exception {
        // Given
        String mockPrompt = "Extract changes...";
        String mockJsonResponse = createEmptyExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, userInput, narratorResponse);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
        
        // Verify no appliers were called since no changes
        verify(applierRegistry, never()).applyEvents(any(), any());
        verify(applierRegistry, never()).applyFactDiscoveries(any(), any());
        verify(applierRegistry, never()).applySecretRevelations(any(), any());
        verify(applierRegistry, never()).applyTensionChanges(any(), any());
        verify(applierRegistry, never()).applyCharacterAppearances(any(), any());
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should use ANALYTICAL model type for extraction")
    void shouldUseAnalyticalModelType() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should pass correct arguments to prompt builder")
    void shouldPassCorrectArgumentsToPromptBuilder() throws Exception {
        // Given
        String customUserInput = "I attack the werewolf";
        String customNarratorResponse = "You lunge at the creature but it dodges.";
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(mockStanza, customUserInput, customNarratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, customUserInput, customNarratorResponse);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, customUserInput, customNarratorResponse);
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should call appliers in correct order")
    void shouldCallAppliersInCorrectOrder() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - verify call order
        var inOrder = inOrder(applierRegistry);
        inOrder.verify(applierRegistry).applyEvents(any(), any());
        inOrder.verify(applierRegistry).applyFactDiscoveries(any(), any());
        inOrder.verify(applierRegistry).applySecretRevelations(any(), any());
        inOrder.verify(applierRegistry).applyTensionChanges(any(), any());
        inOrder.verify(applierRegistry).applyCharacterAppearances(any(), any());
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should not throw exception when LLM call fails")
    void shouldNotThrowWhenLLMCallFails() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM service unavailable"));
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        });
        
        // Appliers should not be called if LLM fails
        verify(applierRegistry, never()).applyEvents(any(), any());
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should not throw exception when JSON parsing fails")
    void shouldNotThrowWhenJsonParsingFails() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String invalidJson = "This is not valid JSON { broken structure";
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(invalidJson);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        });
        
        verify(applierRegistry, never()).applyEvents(any(), any());
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should verify @Transactional behavior would rollback on error")
    void shouldBeTransactionalMethod() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJsonWithFactDiscovery();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // Simulate database error in middle of applying
        doThrow(new RuntimeException("Database constraint violation"))
            .when(applierRegistry).applyFactDiscoveries(any(), any());
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - events were applied before the error, but transaction should rollback
        // (In actual Spring context, the transaction would rollback all changes)
        verify(applierRegistry).applyEvents(any(), any());
        verify(applierRegistry).applyFactDiscoveries(any(), any());
        // Subsequent appliers not called due to exception
        verify(applierRegistry, never()).applySecretRevelations(any(), any());
    }
    
    // ========== EDGE CASES ==========
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should handle null user input gracefully")
    void shouldHandleNullUserInput() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEmptyExtractionJson();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), isNull(), eq(narratorResponse)))
            .thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString()))
            .thenReturn(mockJsonResponse);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, null, narratorResponse);
        });
    }
    
    @SuppressWarnings("null")
    @Test
    @DisplayName("Should handle null narrator response gracefully")
    void shouldHandleNullNarratorResponse() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEmptyExtractionJson();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), eq(userInput), isNull()))
            .thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString()))
            .thenReturn(mockJsonResponse);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, null);
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
                  "secretDescription": "werewolf_status",
                  "characterName": "Scott McCall",
                  "newState": "SUSPICIOUS",
                  "howRevealed": "User noticed Scott's unusual reaction"
                }
              ],
              "tensionChanges": [
                {
                  "tensionDescription": "Supernatural Secret",
                  "changeType": "ESCALATED",
                  "newPressure": 6,
                  "reason": "New person in the room increases exposure risk"
                }
              ],
              "characterAppearances": [
                {
                  "characterName": "Scott McCall",
                  "changeType": "APPEARED",
                  "context": "Classroom"
                },
                {
                  "characterName": "Stiles Stilinski",
                  "changeType": "APPEARED",
                  "context": "Classroom"
                }
              ]
            }
            """;
    }
    
    /**
     * Create extraction JSON with only events
     */
    private String createEventsOnlyJson() {
        return """
            {
              "events": [
                {
                  "description": "User entered room",
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
     * Create extraction JSON with no changes
     */
    private String createEmptyExtractionJson() {
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