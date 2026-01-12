package com.github.rrousso.erik_core.services;

import com.github.rrousso.erik_core.Entities.SessionState;
import com.github.rrousso.erik_core.Entities.StanzaSetup;
import com.github.rrousso.erik_core.Entities.StanzaStatus;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Spring service for building system prompts with synopsis and recent exchanges included
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
	private String worldSnapshotSynopsis;
    
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
        flagDetectionPrompt = promptLoader.load("analytical/flag_detection.txt"); 
        worldSnapshotSynopsis = promptLoader.load("analytical/world_snapshot_synopsis.txt"); 
        System.out.println("[System] Prompts loaded successfully");
    }

    public String buildFlagDetectionPrompt() {
        return flagDetectionPrompt;
    }
    
    public String buildWorldSnapshotPrompt() {
        return worldSnapshotSynopsis;
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
    
    /**
     * Build system prompt for VOID mode
     * Includes synopsis and recent exchanges as text in the prompt
     */
    public String buildVoidPrompt(SessionState state) {
        StringBuilder prompt = new StringBuilder();
        
        // Base prompt
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(voidModeErik);
        
        // Add synopsis if exists
        String synopsis = state.getVoidHistory().getSynopsis();
        if (!synopsis.isEmpty()) {
            prompt.append("\n\n---\n\n");
            prompt.append("PREVIOUS SNAPSHOT:\n");
            prompt.append(synopsis);
            prompt.append("\n\n---\n\n");
        }
        
        // Add recent exchanges as text
        String recentExchanges = state.getVoidHistory().getRecentExchangesForSystemPrompt();
        if (!recentExchanges.isEmpty()) {
            prompt.append("RECENT EXCHANGES:\n");
            prompt.append(recentExchanges);
            prompt.append("---\n\n");
        }
        
        // Add status-specific context
        if (state.getStanzaStatus() == StanzaStatus.ACTIVE) {
            prompt.append("\n\n");
            prompt.append("CONTEXT: The user has confirmed starting the stanza. ");
            prompt.append("Simply acknowledge their readiness with a brief, enthusiastic response (1-2 sentences). ");
            prompt.append("Do NOT narrate the scene. Do NOT describe what happens next. ");
            prompt.append("The Narrator will handle the actual scene - you are still in planning mode, just wrapping up.");
            
        } else if (state.getStanzaStatus() == StanzaStatus.PAUSED) {
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
        } else if (state.getStanzaStatus() == StanzaStatus.ABANDONED) {
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
     * Includes synopsis and recent exchanges as text in the prompt
     */
    public String buildStanzaPrompt(StanzaSetup setup, SessionState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(fictionalFrame);
        prompt.append("\n\n");
        prompt.append(configService.getUserPersona());
        prompt.append("\n\n");
        prompt.append(stanzaModeNarrator);
        prompt.append("\n\n");
        prompt.append("---\n\n");
        prompt.append(setup.toNarratorContext());
        
        // Add synopsis if exists
        String synopsis = state.getStanzaHistory().getSynopsis();
        if (!synopsis.isEmpty()) {
            prompt.append("\n\n---\n\n");
            prompt.append("PREVIOUS SNAPSHOT:\n");
            prompt.append(synopsis);
            prompt.append("\n\n---\n\n");
        }
        
        // Add recent exchanges as text
        String recentExchanges = state.getStanzaHistory().getRecentExchangesForSystemPrompt();
        if (!recentExchanges.isEmpty()) {
            prompt.append("RECENT EXCHANGES:\n");
            prompt.append(recentExchanges);
            prompt.append("---\n\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * Build prompt for extraction phase
     */
    public String buildExtractionPrompt() {
        return extractionPrompt;
    }
}