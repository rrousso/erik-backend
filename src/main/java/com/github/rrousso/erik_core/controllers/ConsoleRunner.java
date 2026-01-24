package com.github.rrousso.erik_core.controllers;

import com.github.rrousso.erik_core.entities.CommandResult;
import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.services.CommandService;
import com.github.rrousso.erik_core.services.ConfigService;
import com.github.rrousso.erik_core.services.SessionFlowService;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * Main console interface for Erik.
 * 
 * Input processing order:
 * 1. Check for "exit" (special case)
 * 2. Check for "/" commands via CommandService (deterministic)
 * 3. Pass to SessionFlowService for LLM-based processing
 */
@Component
public class ConsoleRunner {
    private final SessionFlowService sessionFlow;
    private final ConfigService configService;
    private final CommandService commandService;
    private final SessionState state = new SessionState();

    public ConsoleRunner(
            SessionFlowService sessionFlow, 
            ConfigService configService,
            CommandService commandService) {  
        this.sessionFlow = sessionFlow;
        this.configService = configService;
        this.commandService = commandService;
    }
    
    public void run() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n=== ERIK - CREATIVE ASSISTANT ===");
        System.out.println("Commands:");
        System.out.println("  Natural language works! Try 'let's begin', 'pause', 'continue', etc.");
        System.out.println("  In stanza: use ((pause)), ((end)), etc. for out-of-character commands");
        System.out.println("  Type /help for system commands");
        System.out.println("  'exit' - close the app\n");
        
        // Erik's greeting
        System.out.println(
        	    "The white infinity of the Void steadies as I come into focus beside you.\n\n" +
        	    "\"Hey,\" I say gently. \"I'm glad you're here. " +
        	    "No rush. We can sit for a bit, or if you have something in mind, " +
        	    "tell me what you want to make and I'll help you shape it.\"\n"
        	);


        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim();

            // Exit check
            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n[Erik] \"Oh! Heading out? It was wonderful creating with you. See you next time!\"\n");
                break;
            }

            // Empty input
            if (userInput.isEmpty()) {
                continue;
            }
            
            // Legacy commands (keep for backwards compatibility, but suggest new syntax)
            if (userInput.equalsIgnoreCase("show persona")) {
                System.out.println("\n" + configService.getUserPersona());
                continue;
            }
            if (userInput.equalsIgnoreCase("list stanzas")) {
                System.out.println("[System] Tip: You can also use /list");
                CommandResult result = commandService.processCommand("/list", state);
                System.out.println(result.getResponse());
                continue;
            }

            // STEP 1: Check for "/" commands (deterministic, no LLM)
            CommandResult commandResult = commandService.processCommand(userInput, state);
            if (commandResult.wasHandled()) {
                System.out.println(commandResult.getResponse());
                continue;
            }

            // STEP 2: Pass to SessionFlowService for LLM-based processing
            String message = sessionFlow.handleUserInput(userInput, state);
            System.out.println(message);
        }

        scanner.close();
    }
}