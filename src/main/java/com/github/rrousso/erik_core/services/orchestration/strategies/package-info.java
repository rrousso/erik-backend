/**
 * Strategy pattern implementations for SessionFlowService operations.
 * 
 * This package contains:
 * - FlowStrategy: Base interface for all strategies
 * - Flag-based strategies: StartStanzaStrategy, PauseStanzaStrategy, etc.
 * - Mode-based strategies: VoidModeStrategy, StanzaModeStrategy
 * - FlowStrategyFactory: Creates the appropriate strategy based on context
 */
package com.github.rrousso.erik_core.services.orchestration.strategies;