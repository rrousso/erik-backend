package com.github.rrousso.erik_core.prompt;

import com.github.rrousso.erik_core.config.ConfigService;
import com.github.rrousso.erik_core.stanza.StanzaSetup;
import com.github.rrousso.erik_core.stanza.StanzaStatus;
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
    private String voidCompletedContext;
    private String stanzaModeNarrator;
    private String extractionPrompt;
    private String detailedSynopsisExtractionPrompt;
    private String quickSynopsisExtractionPrompt;
    
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
        voidCompletedContext = promptLoader.load("erik/void_end_stanza_context.txt");
        stanzaModeNarrator = promptLoader.load("narrator/stanza_narrator.txt");
        extractionPrompt = promptLoader.load("narrator/extraction_prompt.txt");
        detailedSynopsisExtractionPrompt = promptLoader.load("narrator/detailed_synopsis.txt");
        quickSynopsisExtractionPrompt = promptLoader.load("narrator/quick_synopsis.txt");
        System.out.println("[System] Prompts loaded successfully");
    }
    
    /**
     * Build system prompt for VOID mode
     */
    public String buildVoidPrompt(StanzaStatus status) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(voidModeErik);
        
        if (status == StanzaStatus.PAUSED) {
            prompt.append("\n\n");
            prompt.append(voidPausedContext);
        } else if (status == StanzaStatus.COMPLETED) {
            prompt.append("\n\n");
            prompt.append(voidCompletedContext);
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

	public String buildQuickSynopsisPrompt() {
		return quickSynopsisExtractionPrompt;
	}

	public String buildDetailedSynopsisPrompt() {
		return detailedSynopsisExtractionPrompt;
	}
}
