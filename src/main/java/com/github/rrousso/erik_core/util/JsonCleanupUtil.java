package com.github.rrousso.erik_core.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for cleaning and parsing JSON responses from LLMs.
 * 
 * LLMs sometimes wrap JSON responses in markdown code fences like:
 * ```json
 * { "data": "value" }
 * ```
 * 
 * This utility handles:
 * - Removing markdown code fences (```json and ```)
 * - Trimming whitespace
 * - Parsing cleaned JSON into target type
 * 
 * Benefits of this utility:
 * - DRY: No more duplicate cleanup code across services
 * - Testable: Can unit test JSON cleanup logic independently
 * - Maintainable: One place to update if LLM output format changes
 * - Type-safe: Generic method ensures compile-time type safety
 */
public class JsonCleanupUtil {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Clean markdown code fences from JSON string.
     * 
     * Handles:
     * - ```json ... ```
     * - ``` ... ```
     * - Plain JSON (no fences)
     * 
     * @param jsonResponse Raw response from LLM
     * @return Cleaned JSON string ready for parsing
     */
    public static String cleanJsonResponse(String jsonResponse) {
        if (jsonResponse == null) {
            return null;
        }
        
        String cleaned = jsonResponse.trim();
        
        // Remove opening fence
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        // Remove closing fence
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    /**
     * Clean and parse JSON response into target type.
     * 
     * This is the recommended method to use - it combines cleanup and parsing.
     * 
     * @param <T> Target type to parse into
     * @param jsonResponse Raw response from LLM (may include markdown fences)
     * @param targetClass Class of target type
     * @return Parsed object of type T
     * @throws Exception if cleanup or parsing fails
     * 
     * Example usage:
     *   ExtractionResult result = JsonCleanupUtil.parseJson(response, ExtractionResult.class);
     */
    public static <T> T parseJson(String jsonResponse, Class<T> targetClass) throws Exception {
        String cleaned = cleanJsonResponse(jsonResponse);
        return MAPPER.readValue(cleaned, targetClass);
    }
    
    /**
     * Clean and parse JSON response with custom ObjectMapper.
     * 
     * Use this if you need special Jackson configuration.
     * 
     * @param <T> Target type to parse into
     * @param jsonResponse Raw response from LLM
     * @param targetClass Class of target type
     * @param mapper Custom ObjectMapper with your configuration
     * @return Parsed object of type T
     * @throws Exception if cleanup or parsing fails
     */
    public static <T> T parseJson(String jsonResponse, Class<T> targetClass, ObjectMapper mapper) throws Exception {
        String cleaned = cleanJsonResponse(jsonResponse);
        return mapper.readValue(cleaned, targetClass);
    }
    
    /**
     * Validate that a string contains valid JSON (after cleanup).
     * 
     * Useful for defensive programming before attempting to parse.
     * 
     * @param jsonResponse Raw response to validate
     * @return true if response contains valid JSON, false otherwise
     */
    public static boolean isValidJson(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            if (cleaned == null || cleaned.isEmpty()) {
                return false;
            }
            MAPPER.readTree(cleaned);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}