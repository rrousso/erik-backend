package com.github.rrousso.erik_core.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a message in OpenRouter API response.
 * 
 * Example:
 * {
 *   "role": "assistant",
 *   "content": "The actual response text"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    
    private String role;
    private String content;
    
    // Getters and setters
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}