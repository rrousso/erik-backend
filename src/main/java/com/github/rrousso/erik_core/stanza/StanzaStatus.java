package com.github.rrousso.erik_core.stanza;


/**
 * Represents the life cycle state of a Stanza.
 *
 * <p>A stanza starts in {@link #NONE}, becomes {@link #ACTIVE}
 * when the experience begins, can be paused and becomes {@link #PAUSED}
 * and transitions to {@link #ENDED}
 * once the journey is closed.</p>
 *
 * <p>Once a stanza is {@link #ENDED}, it is immutable.</p>
 */

public enum StanzaStatus {
    /** 
     * No stanza has started and User is probably in the planning state
     */
    NONE("None", "No stanza is ongoing"),
    /**
     * The stanza is currently running.
     * Narrative progression and interaction are allowed.
     */
    ACTIVE("Active", "Stanza is currently running"),
    /**
     * The stanza is paused at the moment.
     * Narrative progression is stopped and need to be manually restarted.
     */
    PAUSED("Paused","Stanza is paused at the moment, can be continued"),
    /**
     * The stanza has concluded.
     * Reflection is allowed, but no further progression.
     */
    COMPLETED("Completed", "Stanza has concluded, can't be restarted"),  
	 /**
     * The stanza was abandoned.
     * Reflection can happen, a new stanza can be started .
     */
	ABANDONED("Abandoned", "Stanza was dropped by the User, cant be restarted");  
	
    private final String label;
    private final String description;
    
    StanzaStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
