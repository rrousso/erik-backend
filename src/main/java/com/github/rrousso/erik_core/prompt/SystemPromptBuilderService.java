package com.github.rrousso.erik_core.prompt;

import com.github.rrousso.erik_core.config.ConfigService;
import com.github.rrousso.erik_core.stanza.StanzaSetup;
import com.github.rrousso.erik_core.stanza.StanzaStatus;
import com.github.rrousso.erik_core.state.SessionState;

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
    private String voidAbandonedContext;
    private String stanzaModeNarrator;
    private String extractionPrompt;
    private String detailedSynopsisExtractionPrompt;
    private String quickSynopsisExtractionPrompt;
    private String changeDistillerPrompt;
    private String flagDetectionPrompt;
    
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
        voidAbandonedContext = promptLoader.load("erik/void_abandon_stanza_context.txt");
        stanzaModeNarrator = promptLoader.load("narrator/stanza_narrator.txt");
        extractionPrompt = promptLoader.load("narrator/extraction_prompt.txt");
        detailedSynopsisExtractionPrompt = promptLoader.load("narrator/detailed_synopsis.txt");
        quickSynopsisExtractionPrompt = promptLoader.load("analytical/quick_synopsis.txt");
        changeDistillerPrompt = promptLoader.load("analytical/changes_distiller.txt");
        flagDetectionPrompt = promptLoader.load("analytical/flag_detection.txt"); // ADD THIS
        System.out.println("[System] Prompts loaded successfully");
    }

    // ADD THIS METHOD
    public String buildFlagDetectionPrompt() {
        return flagDetectionPrompt;
    }
    
    /**
     * Build system prompt for VOID mode
     */
    public String buildVoidPrompt(SessionState state) {
        StringBuilder prompt = new StringBuilder();
        
        // Base prompt
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(voidModeErik);
        
        // Add status-specific context
        if (state.getStanzaStatus() == StanzaStatus.ACTIVE) {
            prompt.append("\n\n");
            prompt.append("CONTEXT: The user has confirmed starting the stanza. Do not ask for confirmation. Transition naturally into final setup or closing.");
            
        }else if (state.getStanzaStatus() == StanzaStatus.PAUSED) {
            prompt.append("\n\n");
            prompt.append(voidPausedContext);
            
            // Add paused stanza information
            if (state.getCurrentStanza() != null) {
                prompt.append("\n\n---\n\n");
                prompt.append("## Paused Stanza Context\n\n");
                prompt.append("**Original Setup:**\n");
                prompt.append(state.getCurrentStanza().toNarratorContext());
                
                String stanzaSynopsis = state.getStanzaHistory().getSynopsis();
                if (stanzaSynopsis != null && !stanzaSynopsis.isEmpty()) {
                    prompt.append("\n\n**What happened so far:**\n");
                    prompt.append(stanzaSynopsis);
                }
            }
            
        } else if (state.getStanzaStatus() == StanzaStatus.COMPLETED) {
            prompt.append("\n\n");
            prompt.append(voidCompletedContext);
            
            // Add completed stanza information
            if (state.getCompletedStanza() != null) {
                prompt.append("\n\n---\n\n");
                prompt.append("## Completed Stanza Reference\n\n");
                prompt.append("The user just completed a stanza. Here's what happened:\n\n");
                prompt.append(state.getCompletedStanza().getDetailedSynopsis());
                prompt.append("\n\n---\n\n");
                prompt.append("Use this information to discuss the stanza with the user if they want to reflect on it.");
            }
        }else if (state.getStanzaStatus() == StanzaStatus.ABANDONED) {
        	prompt.append("\n\n");
            prompt.append(voidAbandonedContext);
        	
            prompt.append("\n\n");
            if (state.getCompletedStanza() != null) {
                prompt.append("\n\n---\n\n");
                prompt.append("## Abandoned Stanza Reference\n\n");
                prompt.append(state.getCompletedStanza().getDetailedSynopsis());
                prompt.append("\n\n---\n\n");
                prompt.append("Use this information to discuss the stanza with the user if they want to reflect on it.");
            }
            
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

	public String buildChangeDistillerPrompt() {
		return changeDistillerPrompt;
	}
}
