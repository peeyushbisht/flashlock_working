package com.flashlock.model;

public class SimulationMetrics {
    private Long runId;
    private String status;
    private Long submitted;
    private Long completed;
    private Long success;
    private Long rejected;
    private Double avgWaitMs;
    private Double maxWaitMs;

    public SimulationMetrics(Long runId, String status, Long submitted, Long completed, Long success, Long rejected, Double avgWaitMs, Double maxWaitMs) {
        this.runId = runId;
        this.status = status;
        this.submitted = submitted;
        this.completed = completed;
        this.success = success;
        this.rejected = rejected;
        this.avgWaitMs = avgWaitMs;
        this.maxWaitMs = maxWaitMs;
    }

    public Long getRunId() { return runId; }
    public String getStatus() { return status; }
    public Long getSubmitted() { return submitted; }
    public Long getCompleted() { return completed; }
    public Long getSuccess() { return success; }
    public Long getRejected() { return rejected; }
    public Double getAvgWaitMs() { return avgWaitMs; }
    public Double getMaxWaitMs() { return maxWaitMs; }
}
