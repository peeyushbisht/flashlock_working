package com.flashlock.controller;

import com.flashlock.model.SimulationMetrics;
import com.flashlock.model.SimulationRequest;
import com.flashlock.service.SimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runSimulation(@RequestBody SimulationRequest request) {
        Long runId = simulationService.startSimulation(request);
        return ResponseEntity.ok(Map.of(
                "runId", runId,
                "status", "STARTED",
                "message", "Simulation initiated across C Engine worker pool"
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<SimulationMetrics> getStats(@RequestParam Long runId) {
        return ResponseEntity.ok(simulationService.getMetrics(runId));
    }
}
