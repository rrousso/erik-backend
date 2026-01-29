package com.github.rrousso.erik_core.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.repositories.PersonaRepository;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

/**
 * Spring-managed configuration and user settings service
 * Now with support for dual model configuration
 */
@Service
public class ConfigService {
	
	private final PersonaRepository personaRepository;
	
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    
    private final ErikProperties properties;
    private String userPersona;
    
    public ConfigService(ErikProperties properties, PersonaRepository personaRepository) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.personaRepository = Objects.requireNonNull(personaRepository, "personaRepository cannot be null");
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
        
        long personaCount = personaRepository.count();
        if (personaCount == 0) {
            log.info("No persona found in database, running first-time setup");
            runFirstTimeSetup();
        } else {
            log.info("Loading existing persona from database");
            loadUserPersonaFromDatabase();
        }
        
        // Log configuration
        logConfiguration();
    }
    
    @PostConstruct
    public void testDatabaseConnection() {
        log.info("Testing database connection...");
        // We'll use repositories to test in the next step
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
        
        Persona personaEntity = new Persona(name, pronouns, physicalDesc, otherDetails);
        personaEntity = personaRepository.save(personaEntity);

        // Build persona text for in-memory use
        userPersona = buildPersonaText(personaEntity);

        log.info("User persona saved to database with ID: {}", personaEntity.getId());
        System.out.println("\n✓ Persona saved to database");
        System.out.println("You can view it in your PostgreSQL database.\n");
    }
    
    /**
     * Load persona from database
     */
    private void loadUserPersonaFromDatabase() {
        // add multi-persona later
        List<Persona> personas = personaRepository.findAll();
        
        if (personas.isEmpty()) {
            throw new IllegalStateException("No personas found in database");
        }
        
        Persona persona = personas.get(0);
        
        userPersona = buildPersonaText(persona);
        
        log.info("Loaded persona: {} ({})", persona.getName(), persona.getPronouns());
    }

    /**
     * Build persona text from Persona entity
     */
    private String buildPersonaText(Persona persona) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER IDENTITY:\n");
        
        if (persona.getName() != null && !persona.getName().isEmpty()) {
            sb.append("- Name: ").append(persona.getName()).append("\n");
        }
        
        if (persona.getPronouns() != null && !persona.getPronouns().isEmpty()) {
            sb.append("- Pronouns: ").append(persona.getPronouns()).append("\n");
        }
        
        if (persona.getDescription() != null && !persona.getDescription().isEmpty()) {
            sb.append("- Physical description: ").append(persona.getDescription()).append("\n");
        }
        
        if (persona.getOtherDetails() != null && !persona.getOtherDetails().isEmpty()) {
            sb.append("- Additional details: ").append(persona.getOtherDetails()).append("\n");
        } 
        
        sb.append("\n**CRITICAL PRONOUN USAGE:**\n");
        sb.append("The user's pronouns are: ").append(persona.getPronouns().isEmpty() ? "not specified" : persona.getPronouns()).append("\n");
        sb.append("ALL references to the user MUST use these pronouns.\n");
        sb.append("Characters in scenes MUST use these pronouns when referring to or addressing the user.\n");
        sb.append("Do NOT use 'they' unless the user's pronouns are specifically they/them.\n");
        sb.append("Do NOT default to neutral pronouns - use the specified pronouns.\n");
        sb.append("\nThis is the baseline for all scenes and dialogue.\n");
        sb.append("Characters will interact with the user according to these details.\n");
        
        return sb.toString();
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

}