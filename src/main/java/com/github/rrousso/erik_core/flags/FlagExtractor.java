package com.github.rrousso.erik_core.flags;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting system flags from LLM responses.
 * 
 * Flags are embedded in responses using the format:
 * [FLAGS:FLAG_NAME]
 * 
 * This class separates the narrative content from system flags.
 */
public class FlagExtractor {
    
    // Pattern to match [FLAGS:SOMETHING] at the end of a response
    private static final Pattern FLAG_PATTERN = Pattern.compile(
        "\\[FLAGS:([A-Z_]+)\\]\\s*$",
        Pattern.MULTILINE
    );
    
    /**
     * Result of flag extraction containing both the cleaned narrative
     * and any detected flag.
     */
    public static class ExtractionResult {
        private final String narrative;
        private final Flag flag;
        
        public ExtractionResult(String narrative, Flag flag) {
            this.narrative = narrative;
            this.flag = flag;
        }
        
        public String getNarrative() {
            return narrative;
        }
        
        public Flag getFlag() {
            return flag;
        }
        
        public boolean hasFlag() {
            return flag != Flag.NONE;
        }
    }
    
    /**
     * Enum of all possible system flags
     */
    public enum Flag {
        NONE,               // No flag detected
        START_STANZA,       // User wants to start the stanza
        CONTINUE_STANZA,    // User wants to continue from pause
        PAUSE_STANZA,       // User wants to pause the stanza
        END_STANZA,         // User wants to end the stanza
        ABANDON_STANZA,     // User wants to abandon the stanza
        NOT_IN_STANZA;      // User tried stanza action while in void
        
        /**
         * Parse a flag string into an enum value
         */
        public static Flag fromString(String flagStr) {
            if (flagStr == null || flagStr.trim().isEmpty()) {
                return NONE;
            }
            
            try {
                return Flag.valueOf(flagStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("[Warning] Unknown flag: " + flagStr);
                return NONE;
            }
        }
    }
    
    /**
     * Extract flag and narrative from an LLM response.
     * 
     * @param response The raw LLM response potentially containing a flag
     * @return ExtractionResult with separated narrative and flag
     */
    public static ExtractionResult extract(String response) {
        if (response == null || response.trim().isEmpty()) {
            return new ExtractionResult("", Flag.NONE);
        }
        
        Matcher matcher = FLAG_PATTERN.matcher(response);
        
        if (matcher.find()) {
            // Extract the flag name
            String flagName = matcher.group(1);
            Flag flag = Flag.fromString(flagName);
            
            // Remove the flag from the response to get clean narrative
            String narrative = matcher.replaceAll("").trim();
            
            return new ExtractionResult(narrative, flag);
        }
        
        // No flag found, return entire response as narrative
        return new ExtractionResult(response, Flag.NONE);
    }
    
    /**
     * Quick check if a response contains any flag
     */
    public static boolean containsFlag(String response) {
        if (response == null) return false;
        return FLAG_PATTERN.matcher(response).find();
    }
    
    /**
     * Get just the flag from a response without parsing the narrative
     */
    public static Flag getFlag(String response) {
        if (response == null) return Flag.NONE;
        
        Matcher matcher = FLAG_PATTERN.matcher(response);
        if (matcher.find()) {
            return Flag.fromString(matcher.group(1));
        }
        return Flag.NONE;
    }
}