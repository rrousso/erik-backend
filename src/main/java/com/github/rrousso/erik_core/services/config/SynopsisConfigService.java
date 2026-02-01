package com.github.rrousso.erik_core.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;

import jakarta.annotation.PostConstruct;

/**
 * Service responsible for synopsis generation configuration.
 * 
 * Provides:
 * - Round window size (how many recent exchanges to include)
 * - Round threshold size (when to trigger synopsis generation)
 * 
 * This service is specific to the synopsis feature and helps
 * control the cost/accuracy tradeoff of synopsis generation.
 */
@Service
public class SynopsisConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(SynopsisConfigService.class);
    
    private final ErikProperties properties;
    
    public SynopsisConfigService(ErikProperties properties) {
        this.properties = properties;
    }
    
    @PostConstruct
    public void initialize() {
        logConfiguration();
    }
    
    /**
     * Get the window size for recent exchanges.
     * 
     * This controls how many recent exchanges are included
     * in the synopsis generation prompt.
     * 
     * Default: 8 exchanges
     */
    public int getWindowSize() {
        return properties.getRoundWindowSize();
    }
    
    /**
     * Get the threshold size for triggering synopsis generation.
     * 
     * When conversation history exceeds this threshold,
     * a synopsis is generated to compress the history.
     * 
     * Default: 10 exchanges
     */
    public int getThresholdSize() {
        return properties.getRoundThresholdSize();
    }
    
    /**
     * Log synopsis configuration on startup
     */
    private void logConfiguration() {
        log.info("Synopsis Configuration loaded:");
        log.info("  Round window size: {}", properties.getRoundWindowSize());
        log.info("  Round threshold size: {}", properties.getRoundThresholdSize());
    }
}