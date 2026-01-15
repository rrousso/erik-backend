package com.github.rrousso.erik_core.controllers;

import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.services.SessionFlowService;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * Main console interface for Erik with pre-filter flag detection.
 * Uses analytical model to detect commands before calling narrative models.
 */
@Component
public class ConsoleRunner {
    private final SessionFlowService sessionFlow;
    private final SessionState state = new SessionState();
    
    public ConsoleRunner(
    		SessionFlowService sessionFlow) {
        this.sessionFlow = sessionFlow;


    }
	 public void run() {
	        Scanner scanner = new Scanner(System.in);
	        String message = "";
	        System.out.println("\n=== ERIK - CREATIVE ASSISTANT ===");
	        System.out.println("Commands:");
	        System.out.println("  Natural language works! Try 'let's begin', 'pause', 'continue', etc.");
	        System.out.println("  In stanza: use ((pause)), ((end)), etc. for out-of-character commands");
	        System.out.println("  'exit' - close the app\n");
	        
	        // Erik's greeting
	        System.out.println("[Erik] \"Hey there! ready to create a new adventure to protagonize?\" \n");
	
	        
	        while (true) {
	            System.out.print("> ");
	            String userInput = scanner.nextLine().trim();

	            if (userInput.equalsIgnoreCase("exit")) {
	                System.out.println("\n[Erik] \"Oh! Heading out? It was wonderful creating with you. See you next time!\"\n");
	                break;
	            }
	            
	            if (userInput.isEmpty()) {
	                continue;
	            }
	            
	            message = sessionFlow.handleUserInput(userInput,state);
	            System.out.println(message);
	            
	        }

	        scanner.close();
	    }
 }