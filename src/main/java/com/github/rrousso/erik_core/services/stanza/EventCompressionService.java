package com.github.rrousso.erik_core.services.stanza;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaEvent;

/**
 * Service for compressing accumulated events to prevent database bloat.
 * 
 * PROBLEM:
 * Events accumulate indefinitely during long stanzas:
 * - 50 exchanges = 50+ event records
 * - Database grows continuously
 * - Queries get slower
 * - Context window bloat when loading stanza
 * 
 * SOLUTION:
 * Compress old, non-major events while preserving important ones:
 * - Keep all events from recent exchanges (configurable)
 * - Keep all events marked as "major"
 * - Compress remaining events into summary events grouped by beat
 * 
 * WHEN TO COMPRESS:
 * - Every N exchanges (configurable, default: 20)
 * - On stanza pause
 * - On stanza end
 * 
 * CONFIGURATION:
 * erik.events.keep-recent-exchanges=10     # Always keep last 10 exchanges
 * erik.events.compress-frequency=20        # Compress every 20 exchanges
 * erik.events.always-keep-major=true       # Never compress major events
 */
@Service
@ConfigurationProperties(prefix = "erik.events")
public class EventCompressionService {
    
    private static final Logger log = LoggerFactory.getLogger(EventCompressionService.class);
    
    /**
     * Number of recent exchanges to keep uncompressed.
     * Default: 10
     */
    private int keepRecentExchanges = 10;
    
    /**
     * How often to run compression (in exchanges).
     * Default: 20 (compress every 20 exchanges)
     */
    private int compressFrequency = 20;
    
    /**
     * Whether to always keep major events.
     * Default: true
     */
    private boolean alwaysKeepMajor = true;
    
    /**
     * Compress old events for a stanza.
     * 
     * Rules:
     * - Keep all events from last N exchanges (keepRecentExchanges)
     * - Keep all events marked as "major" (if alwaysKeepMajor)
     * - Group remaining events by beat
     * - Create summary event per beat
     * - Remove individual events
     * 
     * @param stanza The stanza to compress events for
     * @return Number of events compressed
     */
    public int compressEvents(Stanza stanza) {
        int currentExchange = stanza.getCurrentExchange();
        int keepThreshold = currentExchange - keepRecentExchanges;
        
        log.debug("[EventCompression] Starting compression for stanza {} (exchange {}, threshold: {})", 
            stanza.getId(), currentExchange, keepThreshold);
        
        // Get events eligible for compression
        List<StanzaEvent> compressibleEvents = stanza.getEvents().stream()
            .filter(e -> e.getExchangeNumber() < keepThreshold)
            .filter(e -> !alwaysKeepMajor || !e.isMajor())
            .collect(Collectors.toList());
        
        if (compressibleEvents.isEmpty()) {
            log.debug("[EventCompression] No events to compress");
            return 0;
        }
        
        log.info("[EventCompression] Compressing {} events (keeping {} recent, {} major)", 
            compressibleEvents.size(),
            stanza.getEvents().size() - compressibleEvents.size(),
            stanza.getEvents().stream().filter(StanzaEvent::isMajor).count());
        
        // Group by beat
        Map<Integer, List<StanzaEvent>> byBeat = compressibleEvents.stream()
            .collect(Collectors.groupingBy(StanzaEvent::getBeatNumber));
        
        int totalCompressed = 0;
        
        // Create summary events per beat
        for (Map.Entry<Integer, List<StanzaEvent>> entry : byBeat.entrySet()) {
            int beat = entry.getKey();
            List<StanzaEvent> events = entry.getValue();
            
            // Create compressed summary
            String summary = String.format("Beat %d summary (%d events): ", beat, events.size()) +
                events.stream()
                    .map(StanzaEvent::getDescription)
                    .collect(Collectors.joining("; "));
            
            // Truncate if too long
            if (summary.length() > 280) {
                summary = summary.substring(0, 277) + "...";
            }
            
            // Get first exchange number from this group
            int firstExchange = events.stream()
                .mapToInt(StanzaEvent::getExchangeNumber)
                .min()
                .orElse(0);
            
            // Collect all involved characters
            String involvedCharacters = events.stream()
                .map(StanzaEvent::getInvolvedCharacters)
                .filter(chars -> chars != null && !chars.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
            
            // Create summary event
            StanzaEvent compressed = new StanzaEvent();
            compressed.setStanza(stanza);
            compressed.setDescription(summary);
            compressed.setBeatNumber(beat);
            compressed.setExchangeNumber(firstExchange);
            compressed.setMajor(false); // Summary events are never major
            compressed.setInvolvedCharacters(involvedCharacters.isEmpty() ? "COMPRESSED" : involvedCharacters);
            
            // Remove old events
            stanza.getEvents().removeAll(events);
            totalCompressed += events.size();
            
            // Add compressed event
            stanza.getEvents().add(compressed);
            
            log.debug("[EventCompression] Compressed {} events from beat {} into summary", 
                events.size(), beat);
        }
        
        log.info("[EventCompression] Compression complete: {} events → {} summaries (saved {} records)", 
            totalCompressed, byBeat.size(), totalCompressed - byBeat.size());
        
        return totalCompressed;
    }
    
    /**
     * Check if compression should run based on current exchange number.
     * 
     * @param exchangeNumber Current exchange number
     * @return true if compression should run
     */
    public boolean shouldCompress(int exchangeNumber) {
        if (compressFrequency == 0) {
            return false; // Compression disabled
        }
        
        // Compress every N exchanges
        return exchangeNumber % compressFrequency == 0 && exchangeNumber > keepRecentExchanges;
    }
    
    // === GETTERS AND SETTERS ===
    
    public int getKeepRecentExchanges() {
        return keepRecentExchanges;
    }
    
    public void setKeepRecentExchanges(int keepRecentExchanges) {
        if (keepRecentExchanges < 0) {
            throw new IllegalArgumentException("keepRecentExchanges cannot be negative");
        }
        this.keepRecentExchanges = keepRecentExchanges;
    }
    
    public int getCompressFrequency() {
        return compressFrequency;
    }
    
    public void setCompressFrequency(int compressFrequency) {
        if (compressFrequency < 0) {
            throw new IllegalArgumentException("compressFrequency cannot be negative");
        }
        this.compressFrequency = compressFrequency;
    }
    
    public boolean isAlwaysKeepMajor() {
        return alwaysKeepMajor;
    }
    
    public void setAlwaysKeepMajor(boolean alwaysKeepMajor) {
        this.alwaysKeepMajor = alwaysKeepMajor;
    }
}