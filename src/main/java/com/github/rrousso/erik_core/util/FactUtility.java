package com.github.rrousso.erik_core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for Fact entity operations.
 * 
 * CRITICAL: Erik is an emergent narrative system.
 * Facts are created DURING storytelling, not predefined.
 * 
 * The hash-based key allows Gemini to REFERENCE existing facts
 * instead of creating duplicates when the same information
 * comes up in different wordings.
 * 
 * Database constraints enforced:
 * - fact_key: 50 characters max
 * - predicate: 100 characters max  
 * - subject_id: 100 characters max
 */
public class FactUtility {
    
    private static final Logger log = LoggerFactory.getLogger(FactUtility.class);
    
    private static final int MAX_KEY_LENGTH = 50;
    private static final int MAX_PREDICATE_LENGTH = 100;
    private static final int MAX_SUBJECT_ID_LENGTH = 100;
    
    /**
     * Generate a fact key from a description.
     * 
     * Format: "normalized_prefix_HASH8"
     * - Normalized prefix: human-readable (max 41 chars)
     * - Hash: 8 hex chars for uniqueness
     * 
     * Example: "user_has_glowing_red_eyes_a3f8b2c1"
     * 
     * The hash allows Gemini to reference this fact later.
     */
    public static String generateFactKey(String description) {
        if (description == null || description.isEmpty()) {
            return "unknown_fact_" + Integer.toHexString("unknown".hashCode());
        }
        
        String normalized = description.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        
        String hash = String.format("%08x", description.hashCode());
        
        int maxPrefixLength = 41;
        String prefix = normalized.length() > maxPrefixLength 
            ? normalized.substring(0, maxPrefixLength) 
            : normalized;
        
        prefix = prefix.replaceAll("_+$", "");
        String key = prefix + "_" + hash;
        
        if (key.length() > MAX_KEY_LENGTH) {
            key = prefix.substring(0, MAX_KEY_LENGTH - 9) + "_" + hash;
        }
        
        return key;
    }
    
    /**
     * Extract hash from fact key.
     * Used when Gemini references an existing fact by hash.
     */
    public static String extractHash(String factKey) {
        if (factKey == null || factKey.length() < 9) {
            return null;
        }
        
        int lastUnderscore = factKey.lastIndexOf('_');
        if (lastUnderscore == -1 || lastUnderscore + 8 >= factKey.length()) {
            return null;
        }
        
        return factKey.substring(lastUnderscore + 1);
    }
    
    /**
     * Check if a fact key matches a given hash.
     */
    public static boolean matchesHash(String factKey, String hash) {
        if (factKey == null || hash == null) {
            return false;
        }
        
        String extractedHash = extractHash(factKey);
        return hash.equalsIgnoreCase(extractedHash);
    }
    
    public static String truncatePredicate(String predicate, String contextInfo) {
        if (predicate == null) {
            return null;
        }
        
        if (predicate.length() > MAX_PREDICATE_LENGTH) {
            log.warn("[FactUtility] Predicate exceeds {} chars, truncating. Context: {}", 
                MAX_PREDICATE_LENGTH, contextInfo);
            return predicate.substring(0, MAX_PREDICATE_LENGTH);
        }
        
        return predicate;
    }
    
    public static String truncateSubjectId(String subjectId, String contextInfo) {
        if (subjectId == null) {
            return null;
        }
        
        if (subjectId.length() > MAX_SUBJECT_ID_LENGTH) {
            log.warn("[FactUtility] Subject ID exceeds {} chars, truncating. Context: {}", 
                MAX_SUBJECT_ID_LENGTH, contextInfo);
            return subjectId.substring(0, MAX_SUBJECT_ID_LENGTH);
        }
        
        return subjectId;
    }
}