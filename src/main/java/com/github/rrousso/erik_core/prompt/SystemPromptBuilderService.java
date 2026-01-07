package com.github.rrousso.erik_core.prompt;

import com.github.rrousso.erik_core.config.ConfigService;
import com.github.rrousso.erik_core.stanza.StanzaSetup;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Spring service for building system prompts
 */
@Service
public class SystemPromptBuilderService {
    
    private final PromptLoaderService promptLoader;
    private final ConfigService configService;
    
    // Cached prompts
    private String fictionalFrame;
    private String voidModeErik;
    private String voidPausedContext;
    private String stanzaModeNarrator;
    private String extractionPrompt;
    
    public SystemPromptBuilderService(PromptLoaderService promptLoader, ConfigService configService) {
        this.promptLoader = promptLoader;
        this.configService = configService;
    }
    
    @PostConstruct
    public void loadPrompts() {
        System.out.println("[System] Loading prompts...");
        fictionalFrame = promptLoader.load("user/fictional_frame.txt");
        voidModeErik = promptLoader.load("erik/void_mode.txt");
        voidPausedContext = promptLoader.load("erik/void_paused_context.txt");
        stanzaModeNarrator = promptLoader.load("narrator/stanza_narrator.txt");
        extractionPrompt = promptLoader.load("narrator/extraction_prompt.txt");
        System.out.println("[System] Prompts loaded successfully");
    }
    
    /**
     * Build system prompt for VOID mode
     */
    public String buildVoidPrompt(boolean stanzaPaused) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(voidModeErik);
        
        if (stanzaPaused) {
            prompt.append("\n\n");
            prompt.append(voidPausedContext);
        }
        
        return prompt.toString();
    }
    
    /**
     * Build system prompt for STANZA mode
     */
    public String buildStanzaPrompt(StanzaSetup setup) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(stanzaModeNarrator);
        prompt.append("\n\n");
        prompt.append("---\n\n");
        prompt.append(setup.toNarratorContext());
        
        return prompt.toString();
    }
    
    /**
     * Build prompt for extraction phase
     */
    public String buildExtractionPrompt() {
        return extractionPrompt;
    }
}
