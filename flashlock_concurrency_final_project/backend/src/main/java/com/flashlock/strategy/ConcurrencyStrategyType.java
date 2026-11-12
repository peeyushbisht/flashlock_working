package com.flashlock.strategy;

import java.util.Locale;

public enum ConcurrencyStrategyType {
    ATOMIC("Atomic Update"),
    PESSIMISTIC("Pessimistic Locking"),
    OPTIMISTIC("Optimistic Locking");

    private final String displayName;

    ConcurrencyStrategyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ConcurrencyStrategyType fromString(String value) {
        if (value == null || value.isBlank()) {
            return ATOMIC;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PESSIMISTIC")) {
            return PESSIMISTIC;
        }
        if (normalized.startsWith("OPTIMISTIC")) {
            return OPTIMISTIC;
        }
        return ATOMIC;
    }
}
