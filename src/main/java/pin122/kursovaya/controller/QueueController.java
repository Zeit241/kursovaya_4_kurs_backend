package pin122.kursovaya.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pin122.kursovaya.model.QueueEntry;
import pin122.kursovaya.service.QueueService;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<QueueEntry>> getQueueByDoctor(@PathVariable Long doctorId) {
        return ResponseEntity.ok(queueService.getQueueByDoctor(doctorId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QueueEntry> getById(@PathVariable Long id) {
        return queueService.getQueueEntryById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<QueueEntry> addToQueue(@RequestBody QueueEntry entry) {
        return ResponseEntity.ok(queueService.saveQueueEntry(entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFromQueue(@PathVariable Long id) {
        queueService.deleteQueueEntry(id);
        return ResponseEntity.noContent().build();
    }
}
