package com.github.rrousso.erik_core.entities;

public class CompletedStanza {
	
	private final String quickSynopsis;      // 150 words, narrative
	private final StanzaMetadata metadata; // All the information saved from the stanza
    
    public CompletedStanza(String quickSynopsis, StanzaMetadata metadata) {
        this.quickSynopsis = quickSynopsis;
        this.metadata = metadata;
    }
	
	
	public String getQuickSynopsis() {
		return quickSynopsis;
	}

	public StanzaMetadata getMetadata() {
		return metadata;
	}

}
