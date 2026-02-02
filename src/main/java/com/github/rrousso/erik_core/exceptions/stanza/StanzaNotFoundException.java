package com.github.rrousso.erik_core.exceptions.stanza;

public class StanzaNotFoundException extends StanzaException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public StanzaNotFoundException(String message) {
        super("No active Stanza found: " + message);
    }
    
    public StanzaNotFoundException(String message, Throwable cause) {
        super("No active Stanza found: " + message, cause);
    }
}
