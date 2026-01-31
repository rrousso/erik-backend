package com.github.rrousso.erik_core.services.orchestration.strategies;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_core.domain.enums.Flag;
import com.github.rrousso.erik_core.domain.models.SessionState;

/**
 * Factory for selecting the appropriate FlowStrategy based on context.
 * 
 * The Factory Pattern centralizes object creation logic. Instead of the client
 * (SessionFlowService) needing to know which strategy to instantiate based on
 * complex conditions, the factory encapsulates that decision-making.
 * 
 * This factory provides two selection methods:
 * 
 * 1. getStrategyForFlag(Flag) - Returns the strategy for a detected flag
 *    Examples: START_STANZA -> StartStanzaStrategy
 *             PAUSE_STANZA -> PauseStanzaStrategy
 * 
 * 2. getStrategyForMode(SessionState) - Returns the strategy for normal conversation
 *    Examples: VOID mode -> VoidModeStrategy
 *             STANZA mode -> StanzaModeStrategy
 * 
 * Benefits of this approach:
 * - SessionFlowService doesn't need switch statements
 * - Adding new strategies requires no changes to SessionFlowService
 * - All strategy selection logic is centralized in one place
 * - Easy to test - just verify the factory returns the right strategy
 */
@Component
public class FlowStrategyFactory {
    
    private static final Logger log = LoggerFactory.getLogger(FlowStrategyFactory.class);
    
    // Map of flags to their corresponding strategies
    private final Map<Flag, FlowStrategy> flagStrategies;
    
    // Strategies for normal conversation (no flag detected)
    private final VoidModeStrategy voidModeStrategy;
    private final StanzaModeStrategy stanzaModeStrategy;
    
    public FlowStrategyFactory(
            StartStanzaStrategy startStanzaStrategy,
            PauseStanzaStrategy pauseStanzaStrategy,
            ContinueStanzaStrategy continueStanzaStrategy,
            EndStanzaStrategy endStanzaStrategy,
            AbandonStanzaStrategy abandonStanzaStrategy,
            VoidModeStrategy voidModeStrategy,
            StanzaModeStrategy stanzaModeStrategy) {
        
        // Build the flag-to-strategy map
        // This uses Map.of() which is immutable and more efficient
        this.flagStrategies = Map.of(
            Flag.START_STANZA, startStanzaStrategy,
            Flag.PAUSE_STANZA, pauseStanzaStrategy,
            Flag.CONTINUE_STANZA, continueStanzaStrategy,
            Flag.END_STANZA, endStanzaStrategy,
            Flag.ABANDON_STANZA, abandonStanzaStrategy
        );
        
        this.voidModeStrategy = voidModeStrategy;
        this.stanzaModeStrategy = stanzaModeStrategy;
        
        log.info("FlowStrategyFactory initialized with {} flag strategies", flagStrategies.size());
    }
    
    /**
     * Get the strategy for a detected flag.
     * 
     * @param flag The detected flag
     * @return The strategy to handle this flag
     * @throws IllegalArgumentException if flag is NONE or unknown
     */
    public FlowStrategy getStrategyForFlag(Flag flag) {
        if (flag == Flag.NONE) {
            throw new IllegalArgumentException("Cannot get strategy for Flag.NONE - use getStrategyForMode() instead");
        }
        
        FlowStrategy strategy = flagStrategies.get(flag);
        
        if (strategy == null) {
            log.error("No strategy found for flag: {}", flag);
            throw new IllegalArgumentException("Unknown flag: " + flag);
        }
        
        log.debug("Selected strategy {} for flag {}", strategy.getClass().getSimpleName(), flag);
        return strategy;
    }
    
    /**
     * Get the strategy for normal conversation (when no flag is detected).
     * 
     * @param state The current session state
     * @return VoidModeStrategy if in void mode, StanzaModeStrategy if in stanza mode
     */
    public FlowStrategy getStrategyForMode(SessionState state) {
        FlowStrategy strategy = state.isInVoidMode() ? voidModeStrategy : stanzaModeStrategy;
        
        log.debug("Selected strategy {} for mode {}", 
            strategy.getClass().getSimpleName(), 
            state.isInVoidMode() ? "VOID" : "STANZA");
        
        return strategy;
    }
    
    /**
     * Alternative method name for clarity - same as getStrategyForMode().
     */
    public FlowStrategy getStrategyForConversation(SessionState state) {
        return getStrategyForMode(state);
    }
}