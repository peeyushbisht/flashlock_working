package com.flashlock.strategy;

public class StrategyExecutionResult {
    private final boolean success;
    private final String status;
    private final Long orderId;
    private final Integer remainingStock;
    private final Integer version;
    private final boolean conflictDetected;
    private final String message;

    public StrategyExecutionResult(boolean success, String status, Long orderId,
                                   Integer remainingStock, Integer version,
                                   boolean conflictDetected, String message) {
        this.success = success;
        this.status = status;
        this.orderId = orderId;
        this.remainingStock = remainingStock;
        this.version = version;
        this.conflictDetected = conflictDetected;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getStatus() { return status; }
    public Long getOrderId() { return orderId; }
    public Integer getRemainingStock() { return remainingStock; }
    public Integer getVersion() { return version; }
    public boolean isConflictDetected() { return conflictDetected; }
    public String getMessage() { return message; }
}
