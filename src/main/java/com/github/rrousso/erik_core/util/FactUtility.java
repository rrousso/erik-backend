package com.github.rrousso.erik_core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for Fact entity operations.
 * 
 * CRITICAL DESIGN DECISION:
 * ========================
 * We use HASH-BASED fact keys because LLMs never generate identical text.
 * 
 * The Problem:
 * - LLM generates "User has supernatural abilities" → hash A
 * - Later generates "User possesses supernatural powers" → hash B
 * - System thinks these are DIFFERENT facts → duplication!
 * 
 * The Solution:
 * - LLM MUST return the fact hash when referencing existing facts
 * - For NEW facts: LLM provides description, we generate hash
 * - For UPDATES: LLM provides the hash to match against
 * 
 * This requires prompting Gemini to:
 * 1. Return fact hashes in extraction results
 * 2. Reference facts by hash, not by description
 * 
 * Database constraints enforced:
 * - fact_key: 50 characters max
 * - predicate: 100 characters max  
 * - subject_id: 100 characters max
 */
public class FactUtility {
    
    private static final Logger log = LoggerFactory.getLogger(FactUtility.class);
    
    // Database column constraints
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_PREDICATE_LENGTH = 100;
    private static final int MAX_SUBJECT_ID_LENGTH = 100;
    
    /**
     * Generate a fact key from a description.
     * 
     * Uses a hybrid approach:
     * - First ~41 chars: human-readable prefix (normalized description)
     * - Last 8 chars: hash of full description (ensures uniqueness)
     * - 1 char separator (_)
     * Total: 50 chars max
     * 
     * The hash allows LLM to reference this fact later by returning the hash.
     * 
     * Examples:
     * - "Supernatural world exists" → "supernatural_world_exists_a3f8b2c1"
     * - "He lost Alan's phone charger" → "he_lost_alan_s_phone_charger_9d4e2f01"
     * 
     * @param description The fact description
     * @return A unique key under 50 characters (format: "normalized_text_HASH8")
     */
    public static String generateFactKey(String description) {
        if (description == null || description.isEmpty()) {
            return "unknown_fact_" + Integer.toHexString("unknown".hashCode());
        }
        
        // Normalize: lowercase, replace non-alphanumeric with underscore
        String normalized = description.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", ""); // Remove leading/trailing underscores
        
        // Generate hash for uniqueness (8 hex chars)
        String hash = String.format("%08x", description.hashCode());
        
        // Calculate max prefix length (50 - 1 separator - 8 hash = 41)
        int maxPrefixLength = 41;
        
        // Truncate prefix if needed
        String prefix = normalized.length() > maxPrefixLength 
            ? normalized.substring(0, maxPrefixLength) 
            : normalized;
        
        // Remove trailing underscore if truncation created one
        prefix = prefix.replaceAll("_+$", "");
        
        // Combine: prefix_hash
        String key = prefix + "_" + hash;
        
        // Safety check (should always be true, but defensive programming)
        if (key.length() > MAX_KEY_LENGTH) {
            key = prefix.substring(0, MAX_KEY_LENGTH - 9) + "_" + hash;
        }
        
        return key;
    }
    
    /**
     * Extract the hash suffix from a fact key.
     * 
     * This allows the LLM to reference facts by hash in extraction results.
     * 
     * Example:
     * - Key: "supernatural_world_exists_a3f8b2c1"
     * - Returns: "a3f8b2c1"
     * 
     * @param factKey The full fact key
     * @return The 8-character hash suffix, or null if key is invalid
     */
    public static String extractHash(String factKey) {
        if (factKey == null || factKey.length() < 9) {
            return null;
        }
        
        // Hash is last 8 characters (after final underscore)
        int lastUnderscore = factKey.lastIndexOf('_');
        if (lastUnderscore == -1 || lastUnderscore + 8 >= factKey.length()) {
            return null;
        }
        
        return factKey.substring(lastUnderscore + 1);
    }
    
    /**
     * Check if a fact key matches a given hash.
     * 
     * Used when LLM returns a hash to reference an existing fact.
     * 
     * @param factKey The fact key to check
     * @param hash The hash to match (8 hex chars)
     * @return true if the key ends with this hash
     */
    public static boolean matchesHash(String factKey, String hash) {
        if (factKey == null || hash == null) {
            return false;
        }
        
        String extractedHash = extractHash(factKey);
        return hash.equalsIgnoreCase(extractedHash);
    }
    
    /**
     * Truncate and validate a predicate string.
     * 
     * Ensures the predicate fits within the database constraint (100 chars).
     * Logs a warning if truncation occurs.
     * 
     * @param predicate The predicate text
     * @param contextInfo Context for logging (e.g., "KnowledgeTransfer from Derek")
     * @return Truncated predicate (max 100 chars)
     */
    public static String truncatePredicate(String predicate, String contextInfo) {
        if (predicate == null) {
            return null;
        }
        
        if (predicate.length() > MAX_PREDICATE_LENGTH) {
            log.warn("[FactUtility] Predicate exceeds {} chars ({}), truncating. Context: {}", 
                MAX_PREDICATE_LENGTH, 
                predicate.length(), 
                contextInfo);
            return predicate.substring(0, MAX_PREDICATE_LENGTH);
        }
        
        return predicate;
    }
    
    /**
     * Truncate and validate a subject ID string.
     * 
     * Ensures the subject ID fits within the database constraint (100 chars).
     * Logs a warning if truncation occurs.
     * 
     * @param subjectId The subject ID
     * @param contextInfo Context for logging (e.g., "Character name: Derek Hale")
     * @return Truncated subject ID (max 100 chars)
     */
    public static String truncateSubjectId(String subjectId, String contextInfo) {
        if (subjectId == null) {
            return null;
        }
        
        if (subjectId.length() > MAX_SUBJECT_ID_LENGTH) {
            log.warn("[FactUtility] Subject ID exceeds {} chars ({}), truncating. Context: {}", 
                MAX_SUBJECT_ID_LENGTH, 
                subjectId.length(), 
                contextInfo);
            return subjectId.substring(0, MAX_SUBJECT_ID_LENGTH);
        }
        
        return subjectId;
    }
    
    /**
     * Validate a fact key (useful for testing or defensive checks).
     * 
     * @param factKey The key to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidFactKey(String factKey) {
        if (factKey == null || factKey.isEmpty()) {
            return false;
        }
        
        if (factKey.length() > MAX_KEY_LENGTH) {
            return false;
        }
        
        // Should be lowercase_snake_case (alphanumeric + underscores only)
        if (!factKey.matches("^[a-z0-9_]+$")) {
            return false;
        }
        
        return true;
    }
}