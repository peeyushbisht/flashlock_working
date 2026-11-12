package com.flashlock.controller;

import com.flashlock.ipc.EngineIpcClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
@CrossOrigin(origins = "*")
public class SchedulerController {

    private final EngineIpcClient ipcClient;

    public SchedulerController(EngineIpcClient ipcClient) {
        this.ipcClient = ipcClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(describe(ipcClient.isEngineOnline()));
    }

    @PostMapping("/policy")
    public ResponseEntity<Map<String, Object>> setPolicy(@RequestBody Map<String, Object> body) {
        Object policy = body.get("policy");
        if (!(policy instanceof String) || !EngineIpcClient.POLICIES.contains(policy)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "policy must be one of FIFO, PRIORITY, PRIORITY_AGING"));
        }
        int aging = body.get("agingIntervalMs") instanceof Number n ? n.intValue() : ipcClient.getAgingIntervalMs();
        boolean online = ipcClient.configurePolicy((String) policy, aging);
        return ResponseEntity.ok(describe(online));
    }

    private Map<String, Object> describe(boolean online) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineOnline", online);
        out.put("policy", ipcClient.getPolicy());
        out.put("agingIntervalMs", ipcClient.getAgingIntervalMs());
        return out;
    }
}
