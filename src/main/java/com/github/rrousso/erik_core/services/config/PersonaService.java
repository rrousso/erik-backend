package com.github.rrousso.erik_core.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;
import com.github.rrousso.erik_core.persistence.entities.Persona;
import com.github.rrousso.erik_core.persistence.repositories.PersonaRepository;

import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service responsible for managing user persona/identity.
 * 
 * Provides:
 * - User persona retrieval (formatted for prompts)
 * - First-time setup wizard
 * - Persona database interactions
 * 
 * This service handles business logic around user identity,
 * not technical configuration.
 */
@Service
public class PersonaService {
    
    private static final Logger log = LoggerFactory.getLogger(PersonaService.class);
    
    private final PersonaRepository personaRepository;
    private final ErikProperties properties;
    private String userPersona; // Cached formatted persona text
    
    public PersonaService(PersonaRepository personaRepository, ErikProperties properties) {
        this.personaRepository = personaRepository;
        this.properties = properties;
    }
    
    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing PersonaService...");
        
        // Ensure user_data directory exists
        Path userDataPath = Paths.get(properties.getUserDataDir());
        Files.createDirectories(userDataPath);
        log.debug("User data directory: {}", userDataPath.toAbsolutePath());
        
        // Check if persona exists in database
        long personaCount = personaRepository.count();
        if (personaCount == 0) {
            log.info("No persona found in database, running first-time setup");
            runFirstTimeSetup();
        } else {
            log.info("Loading existing persona from database");
            loadUserPersonaFromDatabase();
        }
    }
    
    /**
     * Get user persona text formatted for prompt injection.
     * 
     * This is the primary method used by other services.
     */
    public String getUserPersona() {
        if (userPersona == null || userPersona.isBlank()) {
            log.warn("User persona is empty or null");
            return "USER IDENTITY:\n- No persona configured\n";
        }
        return userPersona;
    }
    
    /**
     * Get the current persona entity from database.
     * 
     * For now, returns the first persona (single-user system).
     * TODO: Support multiple personas later.
     */
    public Persona getCurrentPersona() {
        return personaRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No persona found in database"));
    }
    
    /**
     * Check if a persona exists in the database
     */
    public boolean hasPersona() {
        return personaRepository.count() > 0;
    }
    
    /**
     * Run interactive first-time setup wizard.
     * 
     * Prompts user for:
     * - Name
     * - Pronouns
     * - Physical description (optional)
     * - Other details (optional)
     * 
     * Saves to database and caches formatted text.
     */
    public void runFirstTimeSetup() throws IOException {
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
        
        // Save to database
        Persona personaEntity = new Persona(name, pronouns, physicalDesc, otherDetails);
        personaEntity = personaRepository.save(personaEntity);

        // Build cached persona text
        userPersona = buildPersonaText(personaEntity);

        log.info("User persona saved to database with ID: {}", personaEntity.getId());
        System.out.println("\n✓ Persona saved to database");
        System.out.println("You can view it in your PostgreSQL database.\n");
    }
    
    /**
     * Load persona from database and cache formatted text.
     * 
     * Currently loads the first persona (single-user system).
     * TODO: Add multi-persona support later.
     */
    private void loadUserPersonaFromDatabase() {
        List<Persona> personas = personaRepository.findAll();
        
        if (personas.isEmpty()) {
            throw new IllegalStateException("No personas found in database");
        }
        
        Persona persona = personas.get(0);
        userPersona = buildPersonaText(persona);
        
        log.info("Loaded persona: {} ({})", persona.getName(), persona.getPronouns());
    }

    /**
     * Build formatted persona text from Persona entity.
     * 
     * This text is injected into system prompts to give Erik/Narrator
     * context about the user's identity.
     * 
     * Format:
     * USER IDENTITY:
     * - Name: [name]
     * - Pronouns: [pronouns]
     * - Physical description: [description]
     * - Additional details: [other]
     * 
     * **CRITICAL PRONOUN USAGE:**
     * [Instructions for LLM about pronoun usage]
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
        sb.append("The user's pronouns are: ").append(persona.getPronouns().isEmpty() ? 
            "not specified" : persona.getPronouns()).append("\n");
        sb.append("ALL references to the user MUST use these pronouns.\n");
        sb.append("Characters in scenes MUST use these pronouns when referring to or addressing the user.\n");
        sb.append("Do NOT use 'they' unless the user's pronouns are specifically they/them.\n");
        sb.append("Do NOT default to neutral pronouns - use the specified pronouns.\n");
        sb.append("\nThis is the baseline for all scenes and dialogue.\n");
        sb.append("Characters will interact with the user according to these details.\n");
        
        return sb.toString();
    }
}