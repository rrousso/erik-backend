package com.github.rrousso.erik_core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/**
 * Spring-managed configuration and user settings service
 * Now with support for dual model configuration
 */
@Service
public class ConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    
    private final ErikProperties properties;
    private String userPersona;
    
    public ConfigService(ErikProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
    }
    
    /**
     * Initialize configuration on bean creation
     */
    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing ConfigService...");
        
        // Validate API key is configured
        validateApiKey();
        
        // Ensure user_data directory exists
        Path userDataPath = Paths.get(properties.getUserDataDir());
        Files.createDirectories(userDataPath);
        log.debug("User data directory: {}", userDataPath.toAbsolutePath());
        
        // Check if user persona exists, if not run first-time setup
        Path personaPath = Paths.get(properties.getUserPersonaFile());
        if (!Files.exists(personaPath)) {
            log.info("User persona file not found, running first-time setup");
            runFirstTimeSetup();
        } else {
            log.info("Loading existing user persona from: {}", personaPath);
            loadUserPersona();
        }
        
        // Log configuration
        logConfiguration();
    }
    
    /**
     * Validate API key is configured
     */
    private void validateApiKey() {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("API key is not configured!");
            throw new IllegalStateException(
                "API key must be configured. Set OPENROUTER_API_KEY environment variable or erik.api-key in application.properties"
            );
        }
        log.info("API key configured (length: {} chars)", apiKey.length());
    }
    
    /**
     * Log current configuration
     */
    private void logConfiguration() {
        log.info("Configuration loaded:");
        log.info("  Narrative model: {}", properties.getNarrative().getModel());
        log.info("  Narrative temperature: {}", properties.getNarrative().getTemperature());
        log.info("  Narrative max tokens: {}", properties.getNarrative().getMaxTokens());
        log.info("  Analytical model: {}", properties.getAnalytical().getModel());
        log.info("  Analytical temperature: {}", properties.getAnalytical().getTemperature());
        log.info("  Analytical max tokens: {}", properties.getAnalytical().getMaxTokens());
        log.info("  Round window size: {}", properties.getRoundWindowSize());
        log.info("  Round threshold size: {}", properties.getRoundThresholdSize());
    }
    
    /**
     * First-time setup wizard
     */
    private void runFirstTimeSetup() throws IOException {
        System.out.println("\n=== FIRST TIME SETUP ===");
        System.out.println("Welcome! Let me get to know you a bit so I can personalize your stories.\n");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        System.out.print("What's your name? > ");
        String name = reader.readLine().trim();
        
        System.out.print("What are your pronouns? (e.g., he/him, she/her, they/them) > ");
        String pronouns = reader.readLine().trim();
        
        System.out.print("How would you describe yourself physically? (optional, press enter to skip) > ");
        String physicalDesc = reader.readLine().trim();
        
        System.out.print("Any other details you'd like stories to know about you? (optional) > ");
        String otherDetails = reader.readLine().trim();
        
        // Build persona text
        StringBuilder persona = new StringBuilder();
        persona.append("USER IDENTITY:\n");
        
        if (!name.isEmpty()) {
            persona.append("- Name: ").append(name).append("\n");
        }
        
        if (!pronouns.isEmpty()) {
            persona.append("- Pronouns: ").append(pronouns).append("\n");
        }
        
        if (!physicalDesc.isEmpty()) {
            persona.append("- Physical description: ").append(physicalDesc).append("\n");
        }
        
        if (!otherDetails.isEmpty()) {
            persona.append("- Additional details: ").append(otherDetails).append("\n");
        }
        
        persona.append("\nThis is the baseline for all scenes and dialogue.\n");
        persona.append("Characters will interact with the user according to these details.\n");
        
        // Save to file
        Path personaPath = Paths.get(properties.getUserPersonaFile());
        Files.writeString(personaPath, persona.toString());
        userPersona = persona.toString();
        
        log.info("User persona saved to: {}", personaPath.toAbsolutePath());
        System.out.println("\n✓ Persona saved to " + properties.getUserPersonaFile());
        System.out.println("You can edit this file anytime to update your details.\n");
    }
    
    /**
     * Load user persona from file
     */
    private void loadUserPersona() throws IOException {
        Path personaPath = Paths.get(properties.getUserPersonaFile());
        userPersona = Files.readString(personaPath);
        log.debug("User persona loaded ({} chars)", userPersona.length());
    }
    
    /**
     * Get user persona text
     */
    public String getUserPersona() {
        if (userPersona == null || userPersona.isBlank()) {
            log.warn("User persona is empty or null");
            return "USER IDENTITY:\n- No persona configured\n";
        }
        return userPersona;
    }
    
    // ========== NARRATIVE MODEL CONFIG ==========
    
    public ErikProperties.NarrativeConfig getNarrative() {
        return properties.getNarrative();
    }
    
    // ========== ANALYTICAL MODEL CONFIG ==========
    
    public ErikProperties.AnalyticalConfig getAnalytical() {
        return properties.getAnalytical();
    }
    
    // ========== SHARED CONFIG ==========
    
    /**
     * Get API key
     */
    public String getApiKey() {
        return properties.getApiKey();
    }
    
    /**
     * Get max round window size
     */
    public int getWindowSize() {
        return properties.getRoundWindowSize();
    }
    
    /**
     * Get threshold size
     */
    public int getThresholdSize() {
        return properties.getRoundThresholdSize();
    }
    
    // ========== PERSONA MANAGEMENT ==========
    
    /**
     * Check if persona exists
     */
    public boolean hasPersona() {
        return Files.exists(Paths.get(properties.getUserPersonaFile()));
    }
    
    /**
     * Reset persona (for testing or re-setup)
     */
    public void resetPersona() throws IOException {
        Path personaPath = Paths.get(properties.getUserPersonaFile());
        Files.deleteIfExists(personaPath);
        log.info("User persona deleted, running setup again");
        runFirstTimeSetup();
    }
}