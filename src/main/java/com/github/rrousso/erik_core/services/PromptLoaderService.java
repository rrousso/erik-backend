package com.github.rrousso.erik_core.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Spring service for loading prompt templates from resources
 */
@Service
public class PromptLoaderService {
    
    /**
     * Load a prompt file from resources
     * @param path Path relative to /prompts/ directory
     * @return The prompt text
     */
    public String load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + path);
            
            if (!resource.exists()) {
                throw new RuntimeException("Prompt file not found: prompts/" + path);
            }
            
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }
    
    /**
     * Load a prompt with persona support
     * Uses system property 'persona' to select persona folder
     * @param category Category folder (e.g., "user", "erik", "narrator")
     * @param filename Filename within that category
     * @return The prompt text
     */
    public String loadWithPersona(String category, String filename) {
        String persona = System.getProperty("persona", "default");
        String path = persona + "/" + category + "/" + filename;
        
        try {
            return load(path);
        } catch (RuntimeException e) {
            if (!persona.equals("default")) {
                System.err.println("[Warning] Persona file not found: " + path);
                System.err.println("[Warning] Falling back to default");
                return load("default/" + category + "/" + filename);
            }
            throw e;
        }
    }
}
