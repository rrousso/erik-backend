package com.github.rrousso.erik_core.entities;

/**
 * Enum of all possible system flags
 */
public enum Flag {
    NONE,               // No flag detected
    START_STANZA,       // User wants to start the stanza
    CONTINUE_STANZA,    // User wants to continue from pause
    PAUSE_STANZA,       // User wants to pause the stanza
    END_STANZA,         // User wants to end the stanza
    ABANDON_STANZA;     // User wants to abandon the stanza
    
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