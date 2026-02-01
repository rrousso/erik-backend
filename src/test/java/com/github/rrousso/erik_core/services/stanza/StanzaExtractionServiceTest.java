package com.github.rrousso.erik_core.services.stanza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.dto.extraction.EventExtraction;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.services.llm.LLMClientService;
import com.github.rrousso.erik_core.services.prompt.ExtractionPromptBuilder;
import com.github.rrousso.erik_core.services.stanza.appliers.ExtractionApplierRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Stanza Extraction Service Test")
public class StanzaExtractionServiceTest {
    
    @Mock
    private ExtractionPromptBuilder promptBuilder;
    
    @Mock
    private LLMClientService llmClient;
    
    @Mock
    private ExtractionApplierRegistry applierRegistry;
    
    @Mock
    private Stanza mockStanza;
    
    private StanzaExtractionService service;
    
    private String userInput;
    private String narratorResponse;
    
    @BeforeEach
    void setUp() {
        service = new StanzaExtractionService(promptBuilder, llmClient, applierRegistry);
        
        userInput = "I walk into the classroom";
        narratorResponse = "You enter the classroom and see Scott and Stiles talking by the window.";

    }
    
    // ========== SUCCESSFUL EXTRACTION TESTS ==========
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should successfully extract and apply all change types")
    void shouldExtractAndApplyAllChangeTypes() throws Exception {
        // Given
        String mockPrompt = "Extract changes from this exchange...";
        String mockJsonResponse = createFullExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(promptBuilder).buildPrompt(mockStanza, userInput, narratorResponse);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), eq(mockPrompt), anyString());
        
        // Verify all appliers were called with non-empty lists
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> list.size() == 2));
        verify(applierRegistry).applyKnowledgeTransfers(eq(mockStanza), argThat(list -> list.size() == 1));
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
        verify(applierRegistry).applyKnowledgeTransfers(eq(mockStanza), argThat(List::isEmpty));
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
        verify(applierRegistry, never()).applyKnowledgeTransfers(any(), any());
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
        String mockJsonResponse = createEmptyExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, customUserInput, customNarratorResponse))
            .thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, customUserInput, customNarratorResponse);
        
        // Then
        ArgumentCaptor<String> userInputCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> narratorResponseCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(promptBuilder).buildPrompt(
            eq(mockStanza), 
            userInputCaptor.capture(), 
            narratorResponseCaptor.capture()
        );
        
        assertEquals(customUserInput, userInputCaptor.getValue());
        assertEquals(customNarratorResponse, narratorResponseCaptor.getValue());
    }
    
    // ========== JSON PARSING TESTS ==========
    
    @SuppressWarnings({"null", "unchecked"})
	@Test
    @DisplayName("Should correctly parse JSON with all extraction types")
    void shouldParseJsonWithAllTypes() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - capture what was passed to appliers
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(applierRegistry).applyEvents(eq(mockStanza), eventsCaptor.capture());
        
        List<EventExtraction> capturedEvents = (List<EventExtraction>) eventsCaptor.getValue();
        assertEquals(2, capturedEvents.size());
        assertEquals("User entered the classroom", capturedEvents.get(0).getDescription());
        assertEquals("MINOR", capturedEvents.get(0).getSignificance());
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should handle JSON wrapped in markdown code block")
    void shouldHandleJsonWithMarkdown() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = "```json\n" + createEventsOnlyJson() + "\n```";
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - should successfully parse despite markdown wrapper
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> !list.isEmpty()));
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should handle JSON with extra whitespace")
    void shouldHandleJsonWithExtraWhitespace() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = "\n\n  " + createEventsOnlyJson() + "  \n\n";
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then
        verify(applierRegistry).applyEvents(eq(mockStanza), argThat(list -> !list.isEmpty()));
    }
    
    // ========== ERROR HANDLING TESTS ==========
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should not throw exception when LLM client fails")
    void shouldNotThrowWhenLlmClientFails() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM API Error"));
        
        // When/Then - should not throw, just log error
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        });
        
        // Verify appliers were never called
        verify(applierRegistry, never()).applyEvents(any(), any());
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should not throw exception when prompt builder fails")
    void shouldNotThrowWhenPromptBuilderFails() throws Exception {
        // Given
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse))
            .thenThrow(new RuntimeException("Prompt building error"));
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        });
        
        verify(llmClient, never()).call(any(), any(), any());
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
    @DisplayName("Should not throw exception when applier registry throws")
    void shouldNotThrowWhenApplierRegistryThrows() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        doThrow(new RuntimeException("Database error"))
            .when(applierRegistry).applyEvents(any(), any());
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        });
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
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
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
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, userInput, null);
        });
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should handle empty strings for input and response")
    void shouldHandleEmptyStrings() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEmptyExtractionJson();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), eq(""), eq("")))
            .thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, "", "");
        });
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should handle very long input and response")
    void shouldHandleVeryLongInputAndResponse() throws Exception {
        // Given
        String longInput = "I do this thing. ".repeat(500); // ~9000 characters
        String longResponse = "The narrator describes the scene in great detail. ".repeat(500);
        String mockPrompt = "Extract...";
        String mockJsonResponse = createEventsOnlyJson();
        
        when(promptBuilder.buildPrompt(eq(mockStanza), eq(longInput), eq(longResponse)))
            .thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When/Then
        assertDoesNotThrow(() -> {
            service.extractAndUpdate(mockStanza, longInput, longResponse);
        });
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should throw NullPointerException when stanza is null")
    void shouldThrowWhenStanzaIsNull() {
        // When/Then - @NonNull annotation causes NullPointerException before method body executes
        assertThrows(NullPointerException.class, () -> {
            service.extractAndUpdate(null, userInput, narratorResponse);
        });
    }
    
    // ========== INTEGRATION BEHAVIOR TESTS ==========
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should call appliers in correct order")
    void shouldCallAppliersInCorrectOrder() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - verify call order
        var inOrder = inOrder(applierRegistry);
        inOrder.verify(applierRegistry).applyEvents(any(), any());
        inOrder.verify(applierRegistry).applyKnowledgeTransfers(any(), any());
        inOrder.verify(applierRegistry).applySecretRevelations(any(), any());
        inOrder.verify(applierRegistry).applyTensionChanges(any(), any());
        inOrder.verify(applierRegistry).applyCharacterAppearances(any(), any());
    }
    
    @SuppressWarnings("null")
	@Test
    @DisplayName("Should verify @Transactional behavior would rollback on error")
    void shouldBeTransactionalMethod() throws Exception {
        // Given
        String mockPrompt = "Extract...";
        String mockJsonResponse = createFullExtractionJson();
        
        when(promptBuilder.buildPrompt(mockStanza, userInput, narratorResponse)).thenReturn(mockPrompt);
        when(llmClient.call(any(ModelType.class), anyString(), anyString())).thenReturn(mockJsonResponse);
        
        // Simulate database error in middle of applying
        doThrow(new RuntimeException("Database constraint violation"))
            .when(applierRegistry).applyKnowledgeTransfers(any(), any());
        
        // When
        service.extractAndUpdate(mockStanza, userInput, narratorResponse);
        
        // Then - events were applied before the error, but transaction should rollback
        // (In actual Spring context, the transaction would rollback all changes)
        verify(applierRegistry).applyEvents(any(), any());
        verify(applierRegistry).applyKnowledgeTransfers(any(), any());
        // Subsequent appliers not called due to exception
        verify(applierRegistry, never()).applySecretRevelations(any(), any());
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Create a complete extraction JSON with all change types
     */
    private String createFullExtractionJson() {
        return """
            {
              "events": [
                {
                  "description": "User entered the classroom",
                  "significance": "MINOR",
                  "charactersInvolved": ["User"]
                },
                {
                  "description": "User spotted Scott and Stiles",
                  "significance": "DETAIL",
                  "charactersInvolved": ["User", "Scott McCall", "Stiles Stilinski"]
                }
              ],
              "knowledgeTransfers": [
                {
                  "characterName": "Scott McCall",
                  "whatTheyLearned": "New student entered classroom",
                  "howLearned": "Visual observation"
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
              "knowledgeTransfers": [],
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
              "knowledgeTransfers": [],
              "secretRevelations": [],
              "tensionChanges": [],
              "characterAppearances": []
            }
            """;
    }
}
