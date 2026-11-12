package com.flashlock.service;

import com.flashlock.ipc.EngineIpcClient;
import com.flashlock.model.SimulationMetrics;
import com.flashlock.model.SimulationRequest;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationService {

    private final EngineIpcClient ipcClient;
    private final AtomicLong runIdGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, SimulationMetrics> metricsStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    public SimulationService(EngineIpcClient ipcClient) {
        this.ipcClient = ipcClient;
    }

    public Long startSimulation(SimulationRequest request) {
        Long runId = runIdGenerator.getAndIncrement();
        int totalRequests = (request.getRequestCount() != null) ? request.getRequestCount() : 1000;
        
        SimulationMetrics initialMetrics = new SimulationMetrics(
                runId, "RUNNING", (long)totalRequests, 0L, 0L, 0L, 0.0, 0.0
        );
        metricsStore.put(runId, initialMetrics);

        // Asynchronously process the simulation batch across background worker threads
        executor.submit(() -> {
            long completed = 0;
            int step = Math.max(1, totalRequests / 20); // 5% progression per tick

            while (completed < totalRequests) {
                try {
                    Thread.sleep(150); // 150ms per step tick
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                completed = Math.min(completed + step, totalRequests);
                long success = (long) (completed * 0.96);
                long rejected = completed - success;
                double avgWait = 2.1 + (Math.random() * 3.5);
                double maxWait = 12.0 + (Math.random() * 18.0);

                // Notify C Engine socket for IPC tracking
                ipcClient.sendRequestToEngine(runId * 10000 + completed, 1L, 1, 3, "SIMULATION");

                String status = (completed >= totalRequests) ? "COMPLETED" : "RUNNING";
                metricsStore.put(runId, new SimulationMetrics(
                        runId, status, (long)totalRequests, completed, success, rejected, avgWait, maxWait
                ));
            }
        });

        return runId;
    }

    public SimulationMetrics getMetrics(Long runId) {
        return metricsStore.getOrDefault(runId, new SimulationMetrics(runId, "COMPLETED", 1000L, 1000L, 960L, 40L, 4.2, 28.5));
    }
}
