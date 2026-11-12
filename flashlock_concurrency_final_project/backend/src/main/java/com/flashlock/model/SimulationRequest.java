package com.flashlock.model;

public class SimulationRequest {
    private Integer requestCount;
    private Integer producerThreads;
    private Integer workerThreads;
    private Integer queueCapacity;
    private String policy;
    private Integer agingIntervalMs;
    private Boolean persistOrders;

    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }

    public Integer getProducerThreads() { return producerThreads; }
    public void setProducerThreads(Integer producerThreads) { this.producerThreads = producerThreads; }

    public Integer getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(Integer workerThreads) { this.workerThreads = workerThreads; }

    public Integer getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; }

    public String getPolicy() { return policy; }
    public void setPolicy(String policy) { this.policy = policy; }

    public Integer getAgingIntervalMs() { return agingIntervalMs; }
    public void setAgingIntervalMs(Integer agingIntervalMs) { this.agingIntervalMs = agingIntervalMs; }

    public Boolean getPersistOrders() { return persistOrders; }
    public void setPersistOrders(Boolean persistOrders) { this.persistOrders = persistOrders; }
}
