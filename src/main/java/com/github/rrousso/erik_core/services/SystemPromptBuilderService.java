package com.github.rrousso.erik_core.services;

import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.entities.StanzaRecord;
import com.github.rrousso.erik_core.entities.StanzaStatus;
import com.github.rrousso.erik_core.entities.SessionContext;

import jakarta.annotation.PostConstruct;

/**
 * Spring service for building system prompts with synopsis and recent exchanges included
 */
@Service
public class SystemPromptBuilderService {
    
    private final PromptLoaderService promptLoader;
    
    // Cached prompts
    private String fictionalFrame;
    private String voidPersonality;
    private String voidPausedDirective;
    private String voidCompletedDirective;
    private String voidAbandonedDirective;
    private String voidPlanningDirective;
    private String stanzaModeNarrator;
    private String extractionPrompt;
    private String quickSynopsisExtractionPrompt;
    private String changeDistillerPrompt;
    private String flagDetectionPrompt;
    private String worldSnapshotSynopsis;
    
    public SystemPromptBuilderService(PromptLoaderService promptLoader) {
        this.promptLoader = promptLoader;
    }
    
    @PostConstruct
    public void loadPrompts() {
        System.out.println("[System] Loading prompts...");
        fictionalFrame = promptLoader.load("user/fictional_frame.txt");
        voidPersonality = promptLoader.load("erik/personality.txt");
        voidPausedDirective = promptLoader.load("erik/directive_paused.txt");
        voidCompletedDirective = promptLoader.load("erik/directive_completed.txt");
        voidAbandonedDirective = promptLoader.load("erik/directive_abandoned.txt");
        voidPlanningDirective = promptLoader.load("erik/directive_planning.txt");
        stanzaModeNarrator = promptLoader.load("narrator/stanza_narrator.txt");
        extractionPrompt = promptLoader.load("narrator/extraction_prompt.txt");
        quickSynopsisExtractionPrompt = promptLoader.load("analytical/quick_synopsis.txt");
        changeDistillerPrompt = promptLoader.load("analytical/changes_distiller.txt");
        flagDetectionPrompt = promptLoader.load("analytical/flag_detection.txt"); 
        worldSnapshotSynopsis = promptLoader.load("analytical/world_snapshot_synopsis.txt"); 
        System.out.println("[System] Prompts loaded successfully");
    }

    public String buildFlagDetectionPrompt() {
        return flagDetectionPrompt;
    }
    
    public String buildWorldSnapshotPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(persona);
        prompt.append("\n\n---\n\n");
        
        prompt.append(worldSnapshotSynopsis);
        
        return prompt.toString();
    }
    
    public String buildQuickSynopsisPrompt(String persona) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(persona);
        prompt.append("\n\n---\n\n");
        
        prompt.append(quickSynopsisExtractionPrompt);
        
        return prompt.toString();
    }
    
    public String buildChangeDistillerPrompt() {
        return changeDistillerPrompt;
    }
 
    /**
     * Build prompt for extraction phase
     */
    public String buildExtractionPrompt() {
        return extractionPrompt;
    }
    
    // =============================================================================
    // SessionContext based methods
    // =============================================================================
    
    /**
     * Build system prompt for STANZA mode using SessionContext
     */
    public String buildStanzaPromptFromContext(SessionContext context) {
        if (!context.hasStanzaSetup()) {
            throw new IllegalArgumentException("SessionContext must have a StanzaMetadata for stanza prompt");
        }
        
        return new PromptComposer()
            // Identity layer
            .section(fictionalFrame)
            .section(context.getUserPersona())
            .section(stanzaModeNarrator)
            // Stanza setup
            .divider()
            .section(context.getStanzaSetup().toNarratorContext())
            // Memory layer
            .wrappedLabeledSectionIf("PREVIOUS SNAPSHOT:", context.getSynopsis(), context.hasSynopsis())
            .labeledSectionIf("RECENT EXCHANGES:", context.getRecentExchanges(), context.hasRecentExchanges())
            .dividerIf(context.hasRecentExchanges())
            .build();
    }
    
    /**
     * Build system prompt for VOID mode using SessionContext
     * Now includes loaded stanza memory if present
     */
    public String buildVoidPromptFromContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            // Identity layer
            .section(fictionalFrame)
            .section(context.getUserPersona())
            .section(voidPersonality);
        
        // Loaded stanza memory (from /load command) - add before other memory
        if (context.hasLoadedStanzaMemory()) {
            composer.wrappedLabeledSection("LOADED STANZA REFERENCE:", 
                formatLoadedStanzaMemory(context.getLoadedStanzaMemory()));
        }
        
        // Memory layer
        composer
            .wrappedLabeledSectionIf("PREVIOUS SNAPSHOT:", context.getSynopsis(), context.hasSynopsis())
            .labeledSectionIf("RECENT EXCHANGES:", context.getRecentExchanges(), context.hasRecentExchanges())
            .dividerIf(context.hasRecentExchanges())
            .section(getDirectiveForStatus(context));
        
        return composer.build();
    }
    
    /**
     * Format a loaded StanzaRecord for injection into Erik's prompt
     */
    private String formatLoadedStanzaMemory(StanzaRecord stanza) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("The user has loaded a previous stanza for reference. ");
        sb.append("You can discuss this stanza, use it as inspiration, or help them create something similar or different.\n\n");
        
        if (stanza.getSetting() != null && !stanza.getSetting().isEmpty()) {
            sb.append("**Setting:** ").append(stanza.getSetting()).append("\n");
        }
        if (stanza.getPremise() != null && !stanza.getPremise().isEmpty()) {
            sb.append("**Premise:** ").append(stanza.getPremise()).append("\n");
        }
        if (stanza.getTone() != null && !stanza.getTone().isEmpty()) {
            sb.append("**Tone:** ").append(stanza.getTone()).append("\n");
        }
        if (stanza.getUserRole() != null && !stanza.getUserRole().isEmpty()) {
            sb.append("**User's Role:** ").append(stanza.getUserRole()).append("\n");
        }
        if (stanza.getCharacters() != null && !stanza.getCharacters().isEmpty()) {
            sb.append("**Characters:** ").append(String.join(", ", stanza.getCharacters())).append("\n");
        }
        
        sb.append("\n**What Happened:**\n");
        if (stanza.getQuickSynopsis() != null && !stanza.getQuickSynopsis().isEmpty()) {
            sb.append(stanza.getQuickSynopsis());
        } else {
            sb.append("[No synopsis available]");
        }
        
        return sb.toString();
    }
    
    /**
     * Build status-specific context section for void mode
     * Returns empty string if no special context needed
     */
    private String getDirectiveForStatus(SessionContext context) {
        StanzaStatus status = context.getStanzaStatus();
        
        return switch (status) {
            case ACTIVE -> buildActiveStatusContext();
            case PAUSED -> buildPausedStatusContext(context);
            case COMPLETED -> buildCompletedStatusContext(context);
            case ABANDONED -> buildAbandonedStatusContext(context);
            case NONE -> buildPlanningContext(context);
        };
    }
    
    private String buildPlanningContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            .section(voidPlanningDirective);
        
        // Add hint if user has loaded a stanza
        if (context.hasLoadedStanzaMemory()) {
            composer.section(
                "\nNote: The user has loaded a previous stanza for reference. " +
                "Feel free to discuss it, suggest variations, or help them explore similar themes. " +
                "Don't force the conversation toward it - let them guide."
            );
        }
        
        return composer.build();
    }

    private String buildActiveStatusContext() {
        return new PromptComposer()
            .section("CONTEXT: The user has confirmed starting the stanza. " +
                    "Simply acknowledge their readiness with a brief, enthusiastic response (1-2 sentences). " +
                    "Do NOT narrate the scene. Do NOT describe what happens next. " +
                    "The Narrator will handle the actual scene - you are still in planning mode, just wrapping up.")
            .build();
    }
    
    private String buildPausedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            .section(voidPausedDirective);
        
        if (context.hasStanzaSetup()) {
            composer
                .divider()
                .section("## Paused Stanza Context")
                .labeledSection("**Original Setup:**", context.getStanzaSetup().toNarratorContext());
         
            if (context.hasSynopsis()) {
                composer.labeledSection("**What happened so far:**", context.getSynopsis());
            }
        }
        
        return composer.build();
    }
    
    private String buildCompletedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            .section(voidCompletedDirective);
        
        if (context.hasCompletedStanza()) {
            composer
                .divider()
                .section("## Completed Stanza Reference")
                .section("The user just completed a stanza. Here's what happened:")
                .section(context.getCompletedStanza().getMetadata().toNarratorContext())
                .divider()
                .section("Use this information to discuss the stanza with the user if they want to reflect on it.");
        }
        
        return composer.build();
    }
    
    private String buildAbandonedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            .section(voidAbandonedDirective)
            .section(voidPlanningDirective);
        
        if (context.hasCompletedStanza()) {
            composer
                .divider()
                .section("## Abandoned Stanza Reference")
                .section(context.getCompletedStanza().getMetadata().toNarratorContext())
                .divider()
                .section("Use this information to discuss the stanza with the user if they want to reflect on it.");
        }
        
        return composer.build();
    }
}