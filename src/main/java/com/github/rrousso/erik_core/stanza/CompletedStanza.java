package com.github.rrousso.erik_core.stanza;

public class CompletedStanza {
	
	private final String quickSynopsis;      // 150 words, narrative
	private final String detailedSynopsis;   // Structured, comprehensive
	private final StanzaSetup originalSetup; // The setup that started it
    
    public CompletedStanza(String quickSynopsis, String detailedSynopsis, StanzaSetup originalSetup) {
        this.quickSynopsis = quickSynopsis;
        this.detailedSynopsis = detailedSynopsis;
        this.originalSetup = originalSetup;
    }
	
	
	public String getQuickSynopsis() {
		return quickSynopsis;
	}

	public String getDetailedSynopsis() {
		return detailedSynopsis;
	}

	public StanzaSetup getOriginalSetup() {
		return originalSetup;
	}

}
