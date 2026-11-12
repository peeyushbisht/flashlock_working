package com.flashlock.model;

public class PurchaseResponse {
    private Long requestId;
    private String status;
    private String strategy;
    private Long orderId;
    private Integer remainingStock;
    private Integer version;
    private boolean conflictDetected;
    private String message;
    private boolean scheduled;
    private Long queueWaitMs;
    private String schedulerPolicy;

    public PurchaseResponse(Long requestId, String status, String strategy, Long orderId,
                            Integer remainingStock, Integer version, boolean conflictDetected, String message) {
        this.requestId = requestId;
        this.status = status;
        this.strategy = strategy;
        this.orderId = orderId;
        this.remainingStock = remainingStock;
        this.version = version;
        this.conflictDetected = conflictDetected;
        this.message = message;
    }

    public Long getRequestId() { return requestId; }
    public String getStatus() { return status; }
    public String getStrategy() { return strategy; }
    public Long getOrderId() { return orderId; }
    public Integer getRemainingStock() { return remainingStock; }
    public Integer getVersion() { return version; }
    public boolean isConflictDetected() { return conflictDetected; }
    public String getMessage() { return message; }

    public boolean isScheduled() { return scheduled; }
    public Long getQueueWaitMs() { return queueWaitMs; }
    public String getSchedulerPolicy() { return schedulerPolicy; }

    public PurchaseResponse withScheduling(boolean scheduled, Long queueWaitMs, String schedulerPolicy) {
        this.scheduled = scheduled;
        this.queueWaitMs = queueWaitMs;
        this.schedulerPolicy = schedulerPolicy;
        return this;
    }
}
