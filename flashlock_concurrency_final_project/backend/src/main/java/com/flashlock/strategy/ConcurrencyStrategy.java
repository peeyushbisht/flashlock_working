package com.flashlock.strategy;

public interface ConcurrencyStrategy {
    ConcurrencyStrategyType getType();
    StrategyExecutionResult execute(Long requestId, Long productId, int quantity);
}
