package com.github.rrousso.erik_core.exceptions.stanza;

/**
 * Thrown when stanza state transition is invalid.
 */
public class StanzaStateException extends StanzaException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public StanzaStateException(String message) {
        super("Invalid stanza state: " + message);
    }
}