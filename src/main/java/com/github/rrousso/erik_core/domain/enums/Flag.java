package com.github.rrousso.erik_core.domain.enums;

/**
 * Flags detected in user input that trigger special behavior.
 * 
 * These are OOC (out of character) commands that affect the stanza lifecycle.
 */
public enum Flag {
    /**
     * No special flag detected - normal narration
     */
    NONE,
    
    /**
     * User wants to start the stanza (begin narration)
     * Examples: "yes", "let's begin", "start the stanza"
     */
    START_STANZA,
    
    /**
     * User wants to pause the stanza to discuss changes with Erik
     * Examples: ((pause)), "I want to pause"
     */
    PAUSE_STANZA,
    
    /**
     * User wants to resume a paused stanza
     * Examples: "let's continue", "resume the stanza"
     */
    CONTINUE_STANZA,
    
    /**
     * User wants to end the stanza (complete it)
     * Examples: ((end stanza)), ((end))
     */
    END_STANZA,
    
    /**
     * User wants to abandon the stanza (discard it)
     * Examples: ((abandon)), "never mind, let's start over"
     */
    ABANDON_STANZA,
    
    /**
     * User wants to transition to a new beat (scene change)
     * Examples: 
     * - "I sit down." ((next beat: Let's see what the pack is doing))
     * - ((next beat: Time skip to evening))
     * - "We finish talking." ((new beat: School cafeteria - Afternoon))
     */
    NEXT_BEAT
}
