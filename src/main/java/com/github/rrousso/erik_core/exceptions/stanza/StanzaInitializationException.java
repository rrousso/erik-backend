package com.github.rrousso.erik_core.exceptions.stanza;

/**
 * Thrown when stanza initialization fails.
 */
public class StanzaInitializationException extends StanzaException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public StanzaInitializationException(String message) {
        super("Stanza initialization failed: " + message);
    }
    
    public StanzaInitializationException(String message, Throwable cause) {
        super("Stanza initialization failed: " + message, cause);
    }
}