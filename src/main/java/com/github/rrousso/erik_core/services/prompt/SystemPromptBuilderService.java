package com.github.rrousso.erik_core.services.prompt;

import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.domain.enums.StanzaStatus;
import com.github.rrousso.erik_core.domain.models.SessionContext;
import com.github.rrousso.erik_core.domain.valueobjects.CompletedStanza;

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
     * 
     * UPDATE: Now uses getNarratorContext() which supports both DB-loaded context
     * and InitializedStanza fallback.
     */
    public String buildStanzaPromptFromContext(SessionContext context) {
        // Get narrator context - either from DB or InitializedStanza
        String narratorContext = context.getNarratorContext();
        
        if (narratorContext == null || narratorContext.isEmpty()) {
            throw new IllegalArgumentException("SessionContext must have narrator context (from DB or InitializedStanza)");
        }
        
        return new PromptComposer()
            // Identity layer
            .section(fictionalFrame)
            .section(context.getUserPersona())
            .section(stanzaModeNarrator)
            // Stanza setup - now uses the unified getNarratorContext()
            .divider()
            .section(narratorContext)
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
    private String formatLoadedStanzaMemory(Stanza stanza) {
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
        
        // Show characters with rich details
        if (stanza.getCharacters() != null && !stanza.getCharacters().isEmpty()) {
            sb.append("\n**Characters:**\n");
            for (var character : stanza.getCharacters()) {
                sb.append("- ").append(character.getName());
                if (character.getCanonRole() != null && !character.getCanonRole().isEmpty()) {
                    sb.append(" (").append(character.getCanonRole()).append(")");
                }
                if (character.getEmotionalState() != null && !character.getEmotionalState().isEmpty()) {
                    sb.append(" - ").append(character.getEmotionalState());
                }
                sb.append("\n");
            }
        }
        
        // Show active tensions if any
        var activeTensions = stanza.getActiveTensions();
        if (activeTensions != null && !activeTensions.isEmpty()) {
            sb.append("\n**Active Tensions:**\n");
            for (var tension : activeTensions) {
                sb.append("- ").append(tension.getDescription());
                sb.append(" (pressure: ").append(tension.getPressure()).append("/10)");
                sb.append("\n");
            }
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
            .section("CRITICAL INSTRUCTION:\n\n" +
                    "The user has confirmed starting the stanza.\n\n" +
                    "Your ONLY job is to acknowledge with 1-2 sentences. Then STOP.\n\n" +
                    "DO NOT:\n" +
                    "- Narrate ANY part of the scene\n" +
                    "- Describe what happens\n" +
                    "- Write in second person ('You feel...', 'You see...')\n" +
                    "- Use phrases like 'STANZA INITIATED' or scene descriptions\n" +
                    "- Write ANYTHING that looks like narrative prose\n\n" +
                    "The Narrator will handle the opening. You are DONE after acknowledging.\n\n" +
                    "Example CORRECT response: 'Perfect! Here we go.'\n" +
                    "Example WRONG response: 'Perfect! Here we go. [scene description]'")
            .build();
    }
    
    private String buildPausedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
            .section(voidPausedDirective);
        
        // Use getNarratorContext() for paused stanza context too
        String narratorContext = context.getNarratorContext();
        if (narratorContext != null && !narratorContext.isEmpty()) {
            composer
                .divider()
                .section("## Paused Stanza Context")
                .labeledSection("**Original Setup:**", narratorContext);
         
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
            CompletedStanza completed = context.getCompletedStanza();
            
            composer
                .divider()
                .section("## Completed Stanza Reference")
                .section("The user just completed a stanza.");
            
            // Include the quick synopsis - this has the actual events and character names
            if (completed.getQuickSynopsis() != null && !completed.getQuickSynopsis().isEmpty()) {
                composer.labeledSection("**What happened:**", completed.getQuickSynopsis());
            }
            
            // Include the initial setup for additional context
            if (completed.getInitializedStanza() != null) {
                composer.labeledSection("**Original Setup:**", 
                    completed.getInitializedStanza().toNarratorContext());
            }
            
            composer
                .divider()
                .section("Use this information to discuss the stanza with the user if they want to reflect on it. " +
                        "Pay attention to character names and events from the synopsis above.");
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
                .section(context.getCompletedStanza().getInitializedStanza().toNarratorContext())
                .divider()
                .section("Use this information to discuss the stanza with the user if they want to reflect on it.");
        }
        
        return composer.build();
    }
}