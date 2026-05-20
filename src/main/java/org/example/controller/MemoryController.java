package org.example.controller;

import org.example.memory.MemoryRecord;
import org.example.memory.MemorySearchRequest;
import org.example.memory.MemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public ResponseEntity<List<MemoryRecord>> list() {
        return ResponseEntity.ok(memoryService.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemoryRecord>> search(@RequestParam(required = false) String serviceName,
                                                     @RequestParam(required = false) String alertName,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(memoryService.search(new MemorySearchRequest(serviceName, alertName, keyword, limit)));
    }

    @PostMapping
    public ResponseEntity<MemoryRecord> remember(@RequestBody MemoryRecord record) {
        return ResponseEntity.ok(memoryService.rememberIncident(
                record.getServiceName(),
                record.getAlertName(),
                record.getRootCause(),
                record.getAction(),
                record.getSummary(),
                record.getEvidence()
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        memoryService.clear();
        return ResponseEntity.noContent().build();
    }
}
