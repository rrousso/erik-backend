package com.github.rrousso.erik_core.console;

import com.github.rrousso.erik_core.conversation.ConversationHistory;
import com.github.rrousso.erik_core.conversation.SynopsisGeneratorService;
import com.github.rrousso.erik_core.llm.LLMClientService;
import com.github.rrousso.erik_core.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_core.stanza.CompletedStanza;
import com.github.rrousso.erik_core.stanza.StanzaExtractorService;
import com.github.rrousso.erik_core.stanza.StanzaSetup;
import com.github.rrousso.erik_core.stanza.StanzaStatus;
import com.github.rrousso.erik_core.state.SessionState;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

/**
 * Main console interface for Erik - Spring managed component
 */
@Component
public class ConsoleRunner {
    
    private final ConversationHistory stanzaHistory;
    private final ConversationHistory voidHistory;
    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final StanzaExtractorService stanzaExtractor;
    private final SynopsisGeneratorService synopsisGenerator;
    
    public ConsoleRunner(
            ConversationHistory conversationHistory,
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            StanzaExtractorService stanzaExtractor,
            SynopsisGeneratorService synopsisGenerator, 
            ConversationHistory stanzaHistory, 
            ConversationHistory voidHistory) {
        this.stanzaHistory = stanzaHistory;
        this.voidHistory = voidHistory;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.stanzaExtractor = stanzaExtractor;
        this.synopsisGenerator = synopsisGenerator;
    }
    
    public void run() {
        SessionState state = new SessionState(stanzaHistory, voidHistory);
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

    void handleUserInput(String userInput, SessionState state) {
        
        if (userInput.equalsIgnoreCase("start stanza")) {
            if (state.isInStanzaMode()) {
                System.out.println("[System] Already in stanza mode.\n");
                return;
            }
            if (state.getStanzaStatus() == StanzaStatus.COMPLETED) {
                System.out.println("\n[System] You've already completed a stanza this session.");
                System.out.println("[System] Take some time to reflect and process the experience.");
                System.out.println("[System] Use 'exit' when you're ready to close the app.\n");
                return;
            }
            startStanza(state);
            return;
        }
        
        if (userInput.toLowerCase().startsWith("*pause*") || 
            userInput.equalsIgnoreCase("pause")) {
            if (state.isInVoidMode()) {
                System.out.println("[System] Already in void mode.\n");
                return;
            }
            pauseStanza(state, userInput);
            return;
        }
        
        if (userInput.equalsIgnoreCase("continue stanza") || 
            userInput.equalsIgnoreCase("continue")) {
            if (state.isInStanzaMode()) {
                System.out.println("[System] Already in stanza mode.\n");
                return;
            }
            if (state.getCurrentStanza() == null) {
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
        
        if (userInput.equalsIgnoreCase("abandon stanza") || 
                userInput.equalsIgnoreCase("abandon")) {
                if (state.isInVoidMode()) {
                    System.out.println("[System] No active stanza to abandon.\n");
                    return;
                }
                abandonStanza(state);
                return;
            }
        
        if (state.isInVoidMode()) {
            handleVoid(userInput, state);
        } else {
            handleStanza(userInput, state);
        }
    }

	// ========== VOID MODE ==========

    void handleVoid(String userInput, SessionState state) {
        try {
            String response = callErik(state, userInput);
            System.out.println("\n[Erik] " + response + "\n");
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    String callErik(SessionState state, String userInput) throws Exception {
    	
    	
        state.getVoidHistory().addUserMessage(userInput);
        
        String systemPrompt = promptBuilder.buildVoidPrompt(state);
        
        List<ConversationHistory.Message> messages = 
            state.getVoidHistory().getMessagesForAPI();
        
        String response = llmClient.callWithHistory(systemPrompt, "", messages);
        
        state.getVoidHistory().addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.getVoidHistory());
        } catch (Exception e) {
            System.err.println("[Warning] Failed to generate synopsis: " + e.getMessage());
        }
        
        return response;
    }

    // ========== STANZA MODE ==========

    void startStanza(SessionState state) {
        System.out.println("\n[System] Extracting stanza details...\n");
        
        try {
            StanzaSetup setup = stanzaExtractor.extract(state.getVoidHistory());
            state.setCurrentStanza(setup);
            state.getVoidHistory().clearHistory();
            state.enterStanzaMode();
            
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
    
    void handleStanza(String userInput, SessionState state) {
        try {
            String narration = callNarrator(state, userInput);
            
            System.out.println("\n[Narration]");
            System.out.println(narration);
            System.out.println();
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    String callNarrator(SessionState state, String userInput) throws Exception {
        state.getStanzaHistory().addUserMessage(userInput);
        
        String systemPrompt = promptBuilder.buildStanzaPrompt(state.getCurrentStanza());
        
        List<ConversationHistory.Message> messages = 
            state.getStanzaHistory().getMessagesForAPI();
        
        String response = llmClient.callWithHistory(systemPrompt, "", messages);
        
        state.getStanzaHistory().addAssistantMessage(response);
        
        try {
            synopsisGenerator.generateSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            System.err.println("[Warning] Failed to generate synopsis: " + e.getMessage());
        }
        
        return response;
    }
    
    void pauseStanza(SessionState state, String userInput) {
        System.out.println("\n[System] Pausing stanza, returning to Void...\n");
        
        
        String pauseMessage = "";
        if (userInput.toLowerCase().startsWith("*pause*")) {
            pauseMessage = userInput.substring(7).trim();
        }
        
        state.enterVoidMode();
        
        try {
            if (pauseMessage.isEmpty()) {
                pauseMessage = "I'd like to pause the stanza.";
            }
            state.setStanzaStatus(StanzaStatus.PAUSED);
            System.out.println("[DEBUG] Pause message: " + pauseMessage);
            String response = callErik(state, pauseMessage);
            System.out.println("\n[Erik] " + response + "\n");
        } catch (Exception e) {
            handleError(e);
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);  // Revert status
        }
    }
    
    void endStanza(SessionState state, String userInput) {
    	try {
	    	String closure = callNarrator(state, 
	    	            "Provide a gentle closing or resolution for this scene, bringing it to a natural end point.");
	    	        System.out.println("\n[Narration - Closing]");
	    	        System.out.println(closure);
	    	        System.out.println();
	    	        
	        System.out.println("\n[System] End stanza, returning to Void...\n");     
	        state.enterVoidMode();
	        
	        CompletedStanza completed = createCompletedStanza(state);
       
        	state.setCompletedStanza(completed);
        	state.setStanzaStatus(StanzaStatus.COMPLETED);
            String response = callErik(state, "I'd like to end this stanza now.");
            System.out.println("\n[System] Here's the quick resume generated\n" + completed.getQuickSynopsis() + "\n");
            System.out.println("\n[Erik] " + response + "\n");
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.enterStanzaMode();
        }

    }
    
    private void abandonStanza(SessionState state) {
        System.out.println("\n[System] Abandoning stanza...\n");
        
        try {
            // Clear stanza without ceremony
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.ABANDONED);
            
            CompletedStanza completed = createCompletedStanza(state);
            state.setCompletedStanza(completed);
           
            // Erik asks if you want to try again
            String response = callErik(state, "I decided to abandon that stanza. It wasn't working for me.");
            System.out.println("\n[Erik] " + response + "\n");
            state.setCurrentStanza(null);
            state.getStanzaHistory().clearHistory();
            state.setCompletedStanza(null);
            state.setStanzaStatus(StanzaStatus.NONE);
        } catch (Exception e) {
            handleError(e);
        }
		
	}

	private CompletedStanza createCompletedStanza(SessionState state) {
		String quickSynopsis = "";
        try {
        	quickSynopsis  = synopsisGenerator.generateQuickSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.enterStanzaMode();
        }
        
        String detailedSypnosis = "";
        try {
        	detailedSypnosis = synopsisGenerator.generateDetailedSynopsis(state.getStanzaHistory());
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to stop stanza,.\n");
            state.enterStanzaMode();
        }
        
        CompletedStanza completed = new CompletedStanza(quickSynopsis, detailedSypnosis, state.getCurrentStanza());
        state.setCompletedStanza(completed);
        state.getStanzaHistory().clearHistory();
		return completed;
	}
    
    void continueStanza(SessionState state) {
        System.out.println("\n[System] Resuming stanza...\n");
        state.enterStanzaMode();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        try {
        	String pauseChanges = synopsisGenerator.generatePauseChanges(state.getVoidHistory());
            String continuation = callNarrator(state, "Continue the scene implementing the following changes: " + pauseChanges);
            System.out.println("\n[Narration]");
            System.out.println(continuation);
            System.out.println();
        } catch (Exception e) {
            handleError(e);
            System.out.println("[System] Failed to continue stanza.\n");
            state.enterVoidMode();
        }
    }

    // ========== ERROR HANDLING ==========

    void handleError(Exception e) {
        System.out.println("\n[ERROR] " + e.getMessage());
        e.printStackTrace();
        System.out.println();
    }
}
