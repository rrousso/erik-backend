package com.github.rrousso.erik_core.services.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_core.dto.openrouter.Choice;
import com.github.rrousso.erik_core.dto.openrouter.Message;
import com.github.rrousso.erik_core.dto.openrouter.OpenRouterError;
import com.github.rrousso.erik_core.dto.openrouter.OpenRouterResponse;


/**
 * Unit tests for OpenRouter API response DTOs and Jackson parsing.
 * 
 * These tests verify that our DTOs correctly deserialize OpenRouter API responses.
 */
@DisplayName("OpenRouter DTO Tests")
public class LLMClientServiceTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }
    
    // ========== SUCCESSFUL RESPONSE TESTS ==========
    
    @Test
    @DisplayName("Should parse successful response with content")
    void shouldParseSuccessfulResponse() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-123",
          "model": "anthropic/claude-sonnet-4.5",
          "created": 1234567890,
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Hello, I am Claude!"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertNotNull(response);
        assertEquals("gen-123", response.getId());
        assertEquals("anthropic/claude-sonnet-4.5", response.getModel());
        assertEquals(1234567890L, response.getCreated());
        
        assertNotNull(response.getChoices());
        assertEquals(1, response.getChoices().size());
        
        Choice choice = response.getChoices().get(0);
        assertEquals(0, choice.getIndex());
        assertEquals("stop", choice.getFinishReason());
        
        Message message = choice.getMessage();
        assertNotNull(message);
        assertEquals("assistant", message.getRole());
        assertEquals("Hello, I am Claude!", message.getContent());
        
        // Test convenience method
        assertEquals("Hello, I am Claude!", response.getContent());
    }
    
    @Test
    @DisplayName("Should parse response with newlines and special characters in content")
    void shouldParseContentWithSpecialCharacters() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-456",
          "model": "anthropic/claude-sonnet-4.5",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "Line 1\\nLine 2\\n\\tTabbed\\n\\"Quoted\\""
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        String content = response.getContent();
        assertNotNull(content);
        assertTrue(content.contains("Line 1"));
        assertTrue(content.contains("Line 2"));
        assertTrue(content.contains("\t"));
        assertTrue(content.contains("\"Quoted\""));
    }
    
    @Test
    @DisplayName("Should handle response with multiple choices (takes first)")
    void shouldHandleMultipleChoices() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-789",
          "model": "anthropic/claude-sonnet-4.5",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "First choice"
              },
              "finish_reason": "stop"
            },
            {
              "message": {
                "role": "assistant",
                "content": "Second choice"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertEquals(2, response.getChoices().size());
        assertEquals("First choice", response.getContent()); // Gets first choice
    }
    
    @Test
    @DisplayName("Should return null for content when no choices")
    void shouldReturnNullWhenNoChoices() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-000",
          "model": "anthropic/claude-sonnet-4.5",
          "choices": []
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertNull(response.getContent());
    }
    
    @Test
    @DisplayName("Should ignore unknown fields in response")
    void shouldIgnoreUnknownFields() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-111",
          "model": "anthropic/claude-sonnet-4.5",
          "unknown_field": "some value",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "Test"
              },
              "finish_reason": "stop",
              "another_unknown": 123
            }
          ],
          "usage": {
            "prompt_tokens": 100,
            "completion_tokens": 50
          }
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertNotNull(response);
        assertEquals("Test", response.getContent());
    }
    
    // ========== ERROR RESPONSE TESTS ==========
    
    @Test
    @DisplayName("Should parse error response")
    void shouldParseErrorResponse() throws Exception {
        // Given
        String jsonResponse = """
        {
          "error": {
            "message": "Invalid API key",
            "type": "invalid_request_error",
            "code": "invalid_api_key"
          }
        }
        """;
        
        // When
        OpenRouterError errorResponse = objectMapper.readValue(jsonResponse, OpenRouterError.class);
        
        // Then
        assertNotNull(errorResponse);
        assertNotNull(errorResponse.getError());
        assertEquals("Invalid API key", errorResponse.getError().getMessage());
        assertEquals("invalid_request_error", errorResponse.getError().getType());
        assertEquals("invalid_api_key", errorResponse.getError().getCode());
    }
    
    @Test
    @DisplayName("Should parse error with minimal fields")
    void shouldParseErrorWithMinimalFields() throws Exception {
        // Given
        String jsonResponse = """
        {
          "error": {
            "message": "Something went wrong"
          }
        }
        """;
        
        // When
        OpenRouterError errorResponse = objectMapper.readValue(jsonResponse, OpenRouterError.class);
        
        // Then
        assertNotNull(errorResponse);
        assertNotNull(errorResponse.getError());
        assertEquals("Something went wrong", errorResponse.getError().getMessage());
    }
    
    @Test
    @DisplayName("Should format error toString correctly")
    void shouldFormatErrorToString() throws Exception {
        // Given
        String jsonResponse = """
        {
          "error": {
            "message": "Rate limit exceeded",
            "type": "rate_limit_error",
            "code": "rate_limit"
          }
        }
        """;
        
        // When
        OpenRouterError errorResponse = objectMapper.readValue(jsonResponse, OpenRouterError.class);
        String errorString = errorResponse.toString();
        
        // Then
        assertTrue(errorString.contains("rate_limit_error"));
        assertTrue(errorString.contains("Rate limit exceeded"));
        assertTrue(errorString.contains("rate_limit"));
    }
    
    // ========== EDGE CASE TESTS ==========
    
    @Test
    @DisplayName("Should handle empty content string")
    void shouldHandleEmptyContent() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-empty",
          "model": "anthropic/claude-sonnet-4.5",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": ""
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertEquals("", response.getContent());
    }
    
    @Test
    @DisplayName("Should handle very long content")
    void shouldHandleVeryLongContent() throws Exception {
        // Given
        String longContent = "A".repeat(10000);
        String jsonResponse = String.format("""
        {
          "id": "gen-long",
          "model": "anthropic/claude-sonnet-4.5",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "%s"
              },
              "finish_reason": "length"
            }
          ]
        }
        """, longContent);
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertEquals(10000, response.getContent().length());
        assertEquals("length", response.getChoices().get(0).getFinishReason());
    }
    
    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void shouldThrowForInvalidJson() {
        // Given
        String invalidJson = "{ this is not valid json }";
        
        // When/Then
        assertThrows(Exception.class, () -> {
            objectMapper.readValue(invalidJson, OpenRouterResponse.class);
        });
    }
    
    @Test
    @DisplayName("Should handle null choices list")
    void shouldHandleNullChoices() throws Exception {
        // Given
        String jsonResponse = """
        {
          "id": "gen-null",
          "model": "anthropic/claude-sonnet-4.5"
        }
        """;
        
        // When
        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);
        
        // Then
        assertNull(response.getContent());
    }
}