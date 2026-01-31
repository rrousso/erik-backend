package com.github.rrousso.erik_core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for state extraction behavior.
 * 
 * Controls how frequently the system extracts state changes from narrative exchanges.
 * 
 * WHY THIS MATTERS:
 * Extraction calls the analytical LLM (Gemini) to parse changes. This:
 * - Costs money (API calls)
 * - Takes time (adds latency)
 * - Updates the database (might be overkill for every exchange)
 * 
 * By making frequency configurable, you can experiment with:
 * - Every exchange (most accurate, highest cost)
 * - Every N exchanges (balanced)
 * - Only on major events (cheapest, least accurate)
 * 
 * EXPERIMENTATION QUESTIONS:
 * - Can the narrator maintain coherence without frequent updates?
 * - Do characters "forget" things if extraction is delayed?
 * - What's the sweet spot for cost vs quality?
 */
@Configuration
@ConfigurationProperties(prefix = "erik.extraction")
public class ExtractionConfig {
    
    /**
     * How often to run extraction (in exchanges).
     * 
     * - 1 = Extract after every exchange (default, most accurate)
     * - 2 = Extract every other exchange
     * - 3 = Extract every third exchange
     * - etc.
     * 
     * Set to 0 to disable extraction entirely (not recommended for production).
     */
    private int frequency = 1;
    
    /**
     * Whether extraction is enabled at all.
     * 
     * If false, no extraction will occur regardless of frequency.
     * Useful for testing narrator behavior without state updates.
     */
    private boolean enabled = true;
    
    /**
     * Whether to always extract on stanza end, regardless of frequency.
     * 
     * Even if frequency is set to 5, the final exchange should probably
     * be extracted to capture the ending state.
     */
    private boolean alwaysExtractOnEnd = true;
    
    /**
     * Whether to always extract on stanza start, regardless of frequency.
     * 
     * The opening narration might set important scene details.
     */
    private boolean alwaysExtractOnStart = true;
    
    // === GETTERS AND SETTERS ===
    
    public int getFrequency() {
        return frequency;
    }
    
    public void setFrequency(int frequency) {
        if (frequency < 0) {
            throw new IllegalArgumentException("Extraction frequency cannot be negative");
        }
        this.frequency = frequency;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isAlwaysExtractOnEnd() {
        return alwaysExtractOnEnd;
    }
    
    public void setAlwaysExtractOnEnd(boolean alwaysExtractOnEnd) {
        this.alwaysExtractOnEnd = alwaysExtractOnEnd;
    }
    
    public boolean isAlwaysExtractOnStart() {
        return alwaysExtractOnStart;
    }
    
    public void setAlwaysExtractOnStart(boolean alwaysExtractOnStart) {
        this.alwaysExtractOnStart = alwaysExtractOnStart;
    }
    
    /**
     * Check if extraction should occur for a given exchange number.
     * 
     * @param exchangeNumber The current exchange number (1-indexed)
     * @param isFirstExchange Whether this is the first exchange (opening narration)
     * @param isFinalExchange Whether this is the final exchange (closing narration)
     * @return true if extraction should occur
     */
    public boolean shouldExtract(int exchangeNumber, boolean isFirstExchange, boolean isFinalExchange) {
        // Disabled globally
        if (!enabled) {
            return false;
        }
        
        // Always extract on start if configured
        if (isFirstExchange && alwaysExtractOnStart) {
            return true;
        }
        
        // Always extract on end if configured
        if (isFinalExchange && alwaysExtractOnEnd) {
            return true;
        }
        
        // Frequency of 0 means never extract (except for start/end overrides above)
        if (frequency == 0) {
            return false;
        }
        
        // Check if this exchange number matches the frequency
        // exchangeNumber is 1-indexed, so exchange 1, 2, 3...
        // frequency 1: extract every exchange (1, 2, 3, 4...)
        // frequency 2: extract every other (2, 4, 6, 8...)
        // frequency 3: extract every third (3, 6, 9, 12...)
        return exchangeNumber % frequency == 0;
    }
}