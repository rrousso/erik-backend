package com.github.rrousso.erik_core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration properties for Erik with dual model support
 */
@Configuration
@ConfigurationProperties(prefix = "erik")
public class ErikProperties {
    
    // Nested model configurations
    private NarrativeConfig narrative = new NarrativeConfig();
    private AnalyticalConfig analytical = new AnalyticalConfig();
    
    // Shared configuration
    private String apiKey;
    private int roundWindowSize = 8;
    private int roundThresholdSize = 10;
    
    // User data paths
    private String userDataDir = "user_data";
    private String userPersonaFile = "user_data/persona.txt";
    
    // Nested configuration classes
    public static class NarrativeConfig {
        private String model = "anthropic/claude-3.5-sonnet";
        private double temperature = 0.9;
        private int maxTokens = 1500;
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public double getTemperature() {
            return temperature;
        }
        
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
        
        public int getMaxTokens() {
            return maxTokens;
        }
        
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
    
    public static class AnalyticalConfig {
        private String model = "google/gemini-2.5-flash";
        private double temperature = 0.3;
        private int maxTokens = 500;
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
        
        public double getTemperature() {
            return temperature;
        }
        
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
        
        public int getMaxTokens() {
            return maxTokens;
        }
        
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
    
    // Getters and setters for nested configs
    public NarrativeConfig getNarrative() {
        return narrative;
    }
    
    public void setNarrative(NarrativeConfig narrative) {
        this.narrative = narrative;
    }
    
    public AnalyticalConfig getAnalytical() {
        return analytical;
    }
    
    public void setAnalytical(AnalyticalConfig analytical) {
        this.analytical = analytical;
    }
    
    // Shared config getters/setters
    public String getApiKey() {
        // Try property first, then environment variable
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("${OPENROUTER_API_KEY}")) {
            return System.getenv("OPENROUTER_API_KEY");
        }
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public int getRoundWindowSize() {
        return roundWindowSize;
    }
    
    public void setRoundWindowSize(int roundWindowSize) {
        this.roundWindowSize = roundWindowSize;
    }
    
    public int getRoundThresholdSize() {
        return roundThresholdSize;
    }
    
    public void setRoundThresholdSize(int roundThresholdSize) {
        this.roundThresholdSize = roundThresholdSize;
    }
    
    public String getUserDataDir() {
        return userDataDir;
    }
    
    public void setUserDataDir(String userDataDir) {
        this.userDataDir = userDataDir;
    }
    
    public String getUserPersonaFile() {
        return userPersonaFile;
    }
    
    public void setUserPersonaFile(String userPersonaFile) {
        this.userPersonaFile = userPersonaFile;
    }
}