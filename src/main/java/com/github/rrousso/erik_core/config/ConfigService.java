package com.github.rrousso.erik_core.config;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;

/**
 * Spring-managed configuration and user settings service
 * Now with support for dual model configuration
 */
@Service
public class ConfigService {
    
    private final ErikProperties properties;
    private String userPersona;
    
    public ConfigService(ErikProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Initialize configuration on bean creation
     */
    @PostConstruct
    public void initialize() throws IOException {
        // Ensure user_data directory exists
        Files.createDirectories(Paths.get(properties.getUserDataDir()));
        
        // Check if user persona exists, if not run first-time setup
        if (!Files.exists(Paths.get(properties.getUserPersonaFile()))) {
            runFirstTimeSetup();
        } else {
            loadUserPersona();
        }
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
        Files.writeString(Paths.get(properties.getUserPersonaFile()), persona.toString());
        userPersona = persona.toString();
        
        System.out.println("\n✓ Persona saved to " + properties.getUserPersonaFile());
        System.out.println("You can edit this file anytime to update your details.\n");
    }
    
    /**
     * Load user persona from file
     */
    private void loadUserPersona() throws IOException {
        userPersona = Files.readString(Paths.get(properties.getUserPersonaFile()));
    }
    
    /**
     * Get user persona text
     */
    public String getUserPersona() {
        return userPersona != null ? userPersona : "USER IDENTITY:\n- No persona configured\n";
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
        Files.deleteIfExists(Paths.get(properties.getUserPersonaFile()));
        runFirstTimeSetup();
    }
}