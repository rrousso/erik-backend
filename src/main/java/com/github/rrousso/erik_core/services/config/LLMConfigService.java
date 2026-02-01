package com.github.rrousso.erik_core.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;
import com.github.rrousso.erik_core.config.ErikProperties.AnalyticalConfig;
import com.github.rrousso.erik_core.config.ErikProperties.NarrativeConfig;

import jakarta.annotation.PostConstruct;

/**
 * Service responsible for LLM model configuration.
 * 
 * Provides:
 * - Narrative model configuration (Claude for creative writing)
 * - Analytical model configuration (Gemini for JSON extraction)
 * - API key management
 * 
 * This service is purely technical configuration - no business logic.
 */
@Service
public class LLMConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(LLMConfigService.class);
    
    private final ErikProperties properties;
    
    public LLMConfigService(ErikProperties properties) {
        this.properties = properties;
    }
    
    @PostConstruct
    public void initialize() {
        validateApiKey();
        logConfiguration();
    }
    
    /**
     * Get narrative model configuration (Claude for creative writing)
     */
    public NarrativeConfig getNarrativeConfig() {
        return properties.getNarrative();
    }
    
    /**
     * Get analytical model configuration (Gemini for extraction/analysis)
     */
    public AnalyticalConfig getAnalyticalConfig() {
        return properties.getAnalytical();
    }
    
    /**
     * Get OpenRouter API key
     * 
     * Priority:
     * 1. Environment variable OPENROUTER_API_KEY
     * 2. Property value from application.yml
     */
    public String getApiKey() {
        return properties.getApiKey();
    }
    
    /**
     * Validate that API key is configured
     * 
     * @throws IllegalStateException if API key is missing
     */
    private void validateApiKey() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("API key is not configured!");
            throw new IllegalStateException(
                "API key must be configured. " +
                "Set OPENROUTER_API_KEY environment variable or erik.api-key in application.properties"
            );
        }
        log.info("API key configured (length: {} chars)", apiKey.length());
    }
    
    /**
     * Log LLM configuration on startup
     */
    private void logConfiguration() {
        log.info("LLM Configuration loaded:");
        log.info("  Narrative model: {}", properties.getNarrative().getModel());
        log.info("  Narrative temperature: {}", properties.getNarrative().getTemperature());
        log.info("  Narrative max tokens: {}", properties.getNarrative().getMaxTokens());
        log.info("  Analytical model: {}", properties.getAnalytical().getModel());
        log.info("  Analytical temperature: {}", properties.getAnalytical().getTemperature());
        log.info("  Analytical max tokens: {}", properties.getAnalytical().getMaxTokens());
    }
}