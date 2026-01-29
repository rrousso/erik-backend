package com.github.rrousso.erik_core.domain.valueobjects;

import com.github.rrousso.erik_core.dto.initialization.InitializedStanza;

public class CompletedStanza {
	
	private final String quickSynopsis;      // 150 words, narrative
	private final InitializedStanza initializedStanza; // All the information saved from the stanza
    
    public CompletedStanza(String quickSynopsis, InitializedStanza initializedStanza) {
        this.quickSynopsis = quickSynopsis;
        this.initializedStanza = initializedStanza;
    }
	
	
	public String getQuickSynopsis() {
		return quickSynopsis;
	}

	public InitializedStanza getInitializedStanza() {
		return initializedStanza;
	}

}
