package com.github.rrousso.erik_core.exceptions.stanza;

/**
 * Thrown when stanza persistence operation fails.
 */
public class StanzaPersistenceException extends StanzaException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public StanzaPersistenceException(String message, Throwable cause) {
        super("Stanza persistence failed: " + message, cause);
    }
}