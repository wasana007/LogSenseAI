package com.logai.controller;

import com.logai.model.LogDocument;
import com.logai.repository.LogSearchRepository;
import com.logai.service.LogStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LogStorageService storageService;
    private final LogSearchRepository logSearchRepository;

    @Value("${kafka.topic.log}")
    private String logTopic;

    public LogController(KafkaTemplate<String, String> kafkaTemplate,
                         LogStorageService storageService,
                         LogSearchRepository logSearchRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.storageService = storageService;
        this.logSearchRepository = logSearchRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> sendLog(@RequestBody String logMessage,
                                                       Authentication authentication) {
        String correlationId = UUID.randomUUID().toString();

        storageService.savePending(correlationId, logMessage);
        kafkaTemplate.send(logTopic, correlationId, logMessage);

        log.info("Logg mottatt | correlationId={} | user={}", correlationId, authentication.getName());

        return ResponseEntity.accepted().body(Map.of(
                "correlationId", correlationId,
                "status", "PENDING"
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<List<LogDocument>> search(@RequestParam String q) {
        return ResponseEntity.ok(logSearchRepository.findByMessageContaining(q));
    }

    @GetMapping("/search/status/{status}")
    public ResponseEntity<List<LogDocument>> searchByStatus(@PathVariable String status) {
        return ResponseEntity.ok(logSearchRepository.findByStatus(status));
    }

    @GetMapping("/search/source/{source}")
    public ResponseEntity<List<LogDocument>> searchBySource(@PathVariable String source) {
        return ResponseEntity.ok(logSearchRepository.findBySource(source));
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<LogDocument> getByCorrelationId(@PathVariable String correlationId) {
        return logSearchRepository.findByCorrelationId(correlationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}