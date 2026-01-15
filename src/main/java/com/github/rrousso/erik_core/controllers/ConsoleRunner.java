package com.github.rrousso.erik_core.controllers;

import com.github.rrousso.erik_core.entities.SessionState;
import com.github.rrousso.erik_core.entities.StanzaRecord;
import com.github.rrousso.erik_core.repositories.StanzaRecordRepository;
import com.github.rrousso.erik_core.services.ConfigService;
import com.github.rrousso.erik_core.services.SessionFlowService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

/**
 * Main console interface for Erik with pre-filter flag detection.
 * Uses analytical model to detect commands before calling narrative models.
 */
@Component
public class ConsoleRunner {
	private final SessionFlowService sessionFlow;
	private final ConfigService configService;
	private final StanzaRecordRepository stanzaRecordRepository;
	private final SessionState state = new SessionState();

	public ConsoleRunner(StanzaRecordRepository stanzaRecordRepository, SessionFlowService sessionFlow, ConfigService configService) {  
	    this.sessionFlow = sessionFlow;
	    this.configService = configService;
		this.stanzaRecordRepository = stanzaRecordRepository;  
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
	            
	            if (userInput.equalsIgnoreCase("show persona")) {
	                // Get persona from config service and display it
	                System.out.println("\n" + configService.getUserPersona());
	                continue;
	            }
	            if (userInput.equalsIgnoreCase("list stanzas")) {
	                listStanzas();
	                continue;
	            }

	            if (userInput.isEmpty()) {
	                continue;
	            }
  
	            message = sessionFlow.handleUserInput(userInput,state);
	            System.out.println(message);
	            
	        }

	        scanner.close();
	    }

	 private void listStanzas() {
		    List<StanzaRecord> stanzas = stanzaRecordRepository.findAll();
		    
		    if (stanzas.isEmpty()) {
		        System.out.println("\n[System] No stanzas saved yet.\n");
		        return;
		    }
		    
		    System.out.println("\n=== SAVED STANZAS ===\n");
		    
		    for (StanzaRecord stanza : stanzas) {
		        System.out.println("ID: " + stanza.getId());
		        System.out.println("Setting: " + stanza.getSetting());
		        System.out.println("Premise: " + stanza.getPremise());
		        System.out.println("Created: " + stanza.getCreatedAt());
		        System.out.println("\nQuick Synopsis:");
		        System.out.println(stanza.getQuickSynopsis());
		        System.out.println("\n---\n");
		    }
		}
 }