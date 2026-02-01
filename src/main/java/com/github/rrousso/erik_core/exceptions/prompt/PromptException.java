package com.github.rrousso.erik_core.exceptions.prompt;

import com.github.rrousso.erik_core.exceptions.ErikException;

/**
 * Base exception for prompt-related errors.
 */
public class PromptException extends ErikException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PromptException(String message) {
        super(message);
    }
    
    public PromptException(String message, Throwable cause) {
        super(message, cause);
    }
}