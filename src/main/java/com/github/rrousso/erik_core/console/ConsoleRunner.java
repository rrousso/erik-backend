package com.github.rrousso.erik_core.console;

import com.github.rrousso.erik_core.conversation.ConversationHistory;
import com.github.rrousso.erik_core.conversation.SynopsisGeneratorService;
import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.stanza.CompletedStanza;
import com.github.rrousso.erik_core.stanza.StanzaExtractorService;
import com.github.rrousso.erik_core.stanza.StanzaSetup;
import com.github.rrousso.erik_core.stanza.StanzaStatus;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

/**
 * Main console interface for Erik - Spring managed component
 */
@Component
public class ConsoleRunner {
    
    enum Mode {
        VOID,
        STANZA
    }
    
    
    static class ErikState {
        Mode mode = Mode.VOID;
        ConversationHistory history;
        StanzaSetup currentStanza = null;
        StanzaStatus stanzaStatus = StanzaStatus.NONE; 
        
        
        ErikState(ConversationHistory history) {
            this.history = history;
        }
    }
    
    private final ConversationHistory conversationHistory;
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final StanzaExtractorService stanzaExtractor;
    private final SynopsisGeneratorService synopsisGenerator;
    
    public ConsoleRunner(
            ConversationHistory conversationHistory,
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            StanzaExtractorService stanzaExtractor,
            SynopsisGeneratorService synopsisGenerator) {
        this.conversationHistory = conversationHistory;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.stanzaExtractor = stanzaExtractor;
        this.synopsisGenerator = synopsisGenerator;
    }
    
    public void run() {
        ErikState state = new ErikState(conversationHistory);
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== ERIK - CREATIVE ASSISTANT ===");
        System.out.println("Commands:");
        System.out.println("  'start stanza' - begin a narrative scene");
        System.out.println("  '*pause* [message]' - pause stanza and talk to Erik");
        System.out.println("  'continue' - resume the stanza");
        System.out.println("  'abandon stanza' - abandon the stanza and start another");
        System.out.println("  'end stanza' - finish the stanza and discuss it with Erik");
        System.out.println("  'exit' - close the app\n");
        
        // Erik's greeting
        try {
            String greeting = callErik(state, "Hello Erik");
            System.out.println("[Erik] " + greeting + "\n");
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to get greeting: " + e.getMessage());
        }

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim();

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n[Erik] Oh! Heading out? It was wonderful creating with you. See you next time!\n");
                break;
            }
            
            if (userInput.isEmpty()) {
                continue;
            }

            handleUserInput(userInput, state);
        }

        scanner.close();
    }

    // ========== CORE ROUTING ==========

    void handleUserInput(String userInput, ErikState state) {
        
        if (userInput.equalsIgnoreCase("start stanza")) {
            if (state.mode == Mode.STANZA) {
                System.out.println("[System] Already in stanza mode.\n");
                return;
            }
            startStanza(state);
            return;
        }
        
        if (userInput.toLowerCase().startsWith("*pause*") || 
            userInput.equalsIgnoreCase("pause")) {
            if (state.mode == Mode.VOID) {
                System.out.println("[System] Already in void mode.\n");
                return;
            }
            pauseStanza(state, userInput);
            return;
        }
        
        if (userInput.equalsIgnoreCase("continue stanza") || 
            userInput.equalsIgnoreCase("continue")) {
            if (state.mode == Mode.STANZA) {
                System.out.println("[System] Already in stanza mode.\n");
                return;
            }
            if (state.currentStanza == null) {
                System.out.println("[System] No stanza to continue. Use 'start stanza' to begin a new one.\n");
                return;
            }
            continueStanza(state);
            return;
        }
        
        if (userInput.equalsIgnoreCase("end stanza")) {
                endStanza(state, userInput);
                return;
        }
        
        if (state.mode == Mode.VOID) {
            handleVoid(userInput, state);
        } else {
            handleStanza(userInput, state);
        }
    }

    // ========== VOID MODE ==========

    void handleVoid(String userInput, ErikState state) {
        try {
            String response = callErik(state, userInput);
            System.out.println("\n[Erik] " + response + "\n");
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    String callErik(ErikState state, String userInput) throws Exception {
        state.history.addUserMessage(userInput);
        
        String systemPrompt = promptBuilder.buildVoidPrompt(state.stanzaStatus);
        
        List<ConversationHistory.Message> messages = 
            state.history.getMessagesForAPI(false);
        
        String response = llmClient.callWithHistory(systemPrompt, "", messages);
        
        state.history.addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.history, false);
        } catch (Exception e) {
            System.err.println("[Warning] Failed to generate synopsis: " + e.getMessage());
        }
        
        return response;
    }

    // ========== STANZA MODE ==========

    void startStanza(ErikState state) {
        System.out.println("\n[System] Extracting stanza details...\n");
        
        try {
            StanzaSetup setup = stanzaExtractor.extract(state.history);
            state.currentStanza = setup;
            
            state.mode = Mode.STANZA;
            state.history.enterStanzaMode();
            
            System.out.println("\n[System] Entering stanza mode...\n");
            
            String opening = callNarrator(state, "Begin the scene.");
            System.out.println("\n[Narration]");
            System.out.println(opening);
            System.out.println();
            
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to start stanza. Remaining in void mode.\n");
        }
    }
    
    void handleStanza(String userInput, ErikState state) {
        try {
            String narration = callNarrator(state, userInput);
            
            System.out.println("\n[Narration]");
            System.out.println(narration);
            System.out.println();
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    String callNarrator(ErikState state, String userInput) throws Exception {
        state.history.addUserMessage(userInput);
        
        String systemPrompt = promptBuilder.buildStanzaPrompt(state.currentStanza);
        
        List<ConversationHistory.Message> messages = 
            state.history.getMessagesForAPI(true);
        
        String response = llmClient.callWithHistory(systemPrompt, "", messages);
        
        state.history.addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.history, true);
        } catch (Exception e) {
            System.err.println("[Warning] Failed to generate synopsis: " + e.getMessage());
        }
        
        return response;
    }
    
    void pauseStanza(ErikState state, String userInput) {
        System.out.println("\n[System] Pausing stanza, returning to Void...\n");
        
        String pauseMessage = "";
        if (userInput.toLowerCase().startsWith("*pause*")) {
            pauseMessage = userInput.substring(7).trim();
        }
        
        state.mode = Mode.VOID;
        state.history.returnToVoid();
        
        try {
            if (pauseMessage.isEmpty()) {
                pauseMessage = "I'd like to pause the stanza.";
            }
            
            String response = callErik(state, pauseMessage);
            System.out.println("\n[Erik] " + response + "\n");
        } catch (Exception e) {
            handleError(e);
            state.mode = Mode.STANZA;
            state.stanzaStatus = StanzaStatus.ACTIVE;  // Revert status
        }
    }
    
    void endStanza(ErikState state, String userInput) {
        System.out.println("\n[System] End stanza, returning to Void...\n");
        
        state.mode = Mode.VOID;
        state.history.returnToVoid();
        
        String quickSynopsis = "";
        try {
        	quickSynopsis  = synopsisGenerator.generateQuickSynopsis(state.history);
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.mode = Mode.STANZA;
        }
        
        String detailedSypnosis = "";
        try {
        	detailedSypnosis = synopsisGenerator.generateDetailedSynopsis(state.history);
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.mode = Mode.STANZA;
        }
        
        CompletedStanza completed = new CompletedStanza(quickSynopsis, detailedSypnosis, state.currentStanza);
        
        try {
        	state.stanzaStatus = StanzaStatus.COMPLETED;
            String response = callErik(state, "I'd like to end this stanza now.");
            System.out.println("\n[System] Here's the quick resume generated\n" + completed.getQuickSynopsis() + "\n");
            System.out.println("\n[Erik] " + response + "\n");
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.mode = Mode.STANZA;
        }

    }
    
    void continueStanza(ErikState state) {
        System.out.println("\n[System] Resuming stanza...\n");
        state.mode = Mode.STANZA;
        
        try {
            String continuation = callNarrator(state, "Continue the scene.");
            System.out.println("\n[Narration]");
            System.out.println(continuation);
            System.out.println();
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to continue stanza.\n");
            state.mode = Mode.VOID;
        }
    }

    // ========== ERROR HANDLING ==========

    void handleError(Exception e) {
        System.out.println("\n[ERROR] " + e.getMessage());
        e.printStackTrace();
        System.out.println();
    }
}
