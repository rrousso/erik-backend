package com.github.rrousso.erik_core.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for a choice in OpenRouter API response.
 * 
 * Example:
 * {
 *   "message": {
 *     "role": "assistant",
 *     "content": "Response text here"
 *   },
 *   "finish_reason": "stop"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Choice {
    
    private Message message;
    
    @JsonProperty("finish_reason")
    private String finishReason;
    
    private Integer index;
    
    // Getters and setters
    
    public Message getMessage() {
        return message;
    }
    
    public void setMessage(Message message) {
        this.message = message;
    }
    
    public String getFinishReason() {
        return finishReason;
    }
    
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
    
    public Integer getIndex() {
        return index;
    }
    
    public void setIndex(Integer index) {
        this.index = index;
    }
}