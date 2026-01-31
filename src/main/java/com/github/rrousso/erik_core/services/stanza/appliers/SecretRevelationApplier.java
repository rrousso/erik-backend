package com.github.rrousso.erik_core.services.stanza.appliers;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.dto.extraction.SecretRevelation;
import com.github.rrousso.erik_core.persistence.entities.CharacterSecretState;
import com.github.rrousso.erik_core.persistence.entities.Secret;
import com.github.rrousso.erik_core.persistence.entities.Stanza;
import com.github.rrousso.erik_core.persistence.entities.StanzaCharacter;

/**
 * Applier for secret revelation extractions.
 * 
 * Updates CharacterSecretState when characters learn or suspect secrets.
 * 
 * Process:
 * 1. Validate description and howRevealed lengths
 * 2. Find the character who learned/suspected the secret
 * 3. Find the Secret by matching fact predicate to secretDescription
 * 4. Find the CharacterSecretState for this character + secret
 * 5. Update the state based on newState from extraction:
 *    - KNOWS: Character now knows the secret (was UNAWARE or SUSPICIOUS)
 *    - SUSPICIOUS: Character suspects something (was UNAWARE)
 * 
 * Secret revelations represent characters learning hidden truths:
 * - "Stiles learned that Scott is a werewolf" → UNAWARE → KNOWS
 * - "Allison suspects her family are hunters" → UNAWARE → SUSPICIOUS
 * - "Derek confirmed the Alpha's identity" → SUSPICIOUS → KNOWS
 * 
 * The system tracks who knows what secrets to prevent information bleeding
 * between characters in the narrative.
 */
@Component
public class SecretRevelationApplier implements ExtractionApplier<SecretRevelation> {
    
    private static final Logger log = LoggerFactory.getLogger(SecretRevelationApplier.class);
    
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_HOWREVEALED_LENGTH = 200;

    @Override
    public void apply(Stanza stanza, SecretRevelation revelation) {
        // Validate lengths
        if (revelation.getSecretDescription() != null && revelation.getSecretDescription().length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[SecretRevelationApplier] Secret description exceeds recommended {} characters: '{}'",
                MAX_DESCRIPTION_LENGTH,
                revelation.getSecretDescription().substring(0, Math.min(100, revelation.getSecretDescription().length())) + "...");
        }
        if (revelation.getHowRevealed() != null && revelation.getHowRevealed().length() > MAX_HOWREVEALED_LENGTH) {
            log.warn("[SecretRevelationApplier] Secret 'howRevealed' exceeds recommended {} characters", 
                MAX_HOWREVEALED_LENGTH);
        }
        
        // 1. Find the character by name
        Optional<StanzaCharacter> charOpt = stanza.getCharacters().stream()
                .filter(c -> c.getName().equalsIgnoreCase(revelation.getCharacterName()))
                .findFirst();
        
        if (!charOpt.isPresent()) {
            log.warn("[SecretRevelationApplier] Character '{}' not found - skipping secret revelation", 
                revelation.getCharacterName());
            return;
        }
        
        StanzaCharacter character = charOpt.get();
        
        // 2. Find the Secret by matching fact predicate to secretDescription
        // The secret description from Gemini should match (or be similar to) the fact's predicate
        Optional<Secret> secretOpt = 
            stanza.getSecrets().stream()
                .filter(s -> {
                    String factPredicate = s.getFact().getPredicate();
                    String secretDesc = revelation.getSecretDescription();
                    
                    // Try exact match first (case-insensitive)
                    if (factPredicate.equalsIgnoreCase(secretDesc)) {
                        return true;
                    }
                    
                    // Try partial match (contains)
                    if (factPredicate.toLowerCase().contains(secretDesc.toLowerCase()) ||
                        secretDesc.toLowerCase().contains(factPredicate.toLowerCase())) {
                        return true;
                    }
                    
                    return false;
                })
                .findFirst();
        
        if (!secretOpt.isPresent()) {
            log.warn("[SecretRevelationApplier] Secret matching '{}' not found in stanza - skipping", 
                revelation.getSecretDescription());
            return;
        }
        
        Secret secret = secretOpt.get();
        
        // 3. Find the CharacterSecretState for this character + secret
        Optional<CharacterSecretState> stateOpt = 
            character.getSecretStates().stream()
                .filter(css -> css.getSecret().getId().equals(secret.getId()))
                .findFirst();
        
        if (!stateOpt.isPresent()) {
            log.warn("[SecretRevelationApplier] No CharacterSecretState found for {} and secret '{}' - skipping", 
                character.getName(), secret.getFact().getPredicate());
            return;
        }
        
        CharacterSecretState secretState = stateOpt.get();
        
        // 4. Update the state based on newState from extraction
        String oldState = secretState.getState();
        String newState = revelation.getNewState();
        
        if ("KNOWS".equalsIgnoreCase(newState)) {
            // Use the convenience method
            secretState.unlock(
                revelation.getHowRevealed(), 
                stanza.getCurrentBeat(), 
                stanza.getCurrentExchange()
            );
            
            log.info("[SecretRevelationApplier] Secret revealed: {} now KNOWS '{}' (was: {})", 
                character.getName(), 
                secret.getFact().getPredicate(), 
                oldState);
            
        } else if ("SUSPICIOUS".equalsIgnoreCase(newState)) {
            // Use the convenience method
            secretState.makeSuspicious(
                revelation.getHowRevealed(), 
                stanza.getCurrentBeat(), 
                stanza.getCurrentExchange()
            );
            
            log.info("[SecretRevelationApplier] Secret hinted: {} is now SUSPICIOUS about '{}' (was: {})", 
                character.getName(), 
                secret.getFact().getPredicate(), 
                oldState);
            
        } else {
            log.warn("[SecretRevelationApplier] Unknown secret state '{}' - expected KNOWS or SUSPICIOUS", newState);
            return;
        }
        
        log.debug("[SecretRevelationApplier] Updated secret state for {}: {} → {}", 
            character.getName(), oldState, newState);
    }
    
    @Override
    public String getTypeName() {
        return "SecretRevelation";
    }
}