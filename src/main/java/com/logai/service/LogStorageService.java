package com.logai.service;

import com.logai.model.LogDocument;
import com.logai.model.LogEntry;
import com.logai.model.LogStatus;
import com.logai.repository.LogRepository;
import com.logai.repository.LogSearchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogStorageService {

    private final LogRepository repository;
    private final LogSearchRepository logSearchRepository;

    public LogStorageService(LogRepository repository,
                             LogSearchRepository logSearchRepository) {
        this.repository = repository;
        this.logSearchRepository = logSearchRepository;
    }

    public void savePending(String correlationId, String message) {
        LogEntry entry = new LogEntry();
        entry.setCorrelationId(correlationId);
        entry.setMessage(message);
        entry.setStatus(LogStatus.PENDING);
        entry.setCreatedAt(LocalDateTime.now());
        repository.save(entry);
    }

    public boolean savePendingIfAbsent(String correlationId, String message) {
        if (repository.existsByCorrelationId(correlationId)) {
            return false;
        }
        savePending(correlationId, message);
        return true;
    }

    public LogEntry saveResult(String correlationId, String result) {
        LogEntry entry = findByCorrelationId(correlationId);
        entry.setStatus(LogStatus.COMPLETED);
        entry.setResult(result);
        entry.setCompletedAt(LocalDateTime.now());
        LogEntry saved = repository.save(entry);
        indexToElasticsearch(saved);
        return saved;
    }

    public LogEntry saveFailed(String correlationId, String errorMessage) {
        LogEntry entry = findByCorrelationId(correlationId);
        entry.setResult(errorMessage);
        entry.setStatus(LogStatus.FAILED);
        entry.setCompletedAt(LocalDateTime.now());
        LogEntry saved = repository.save(entry);
        indexToElasticsearch(saved);
        return saved;
    }

    public LogEntry findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId)
                .orElseThrow(() -> new RuntimeException("Ikke funnet: " + correlationId));
    }

    private void indexToElasticsearch(LogEntry entry) {
        LogDocument doc = new LogDocument();
        doc.setCorrelationId(entry.getCorrelationId());
        doc.setStatus(entry.getStatus().name());
        doc.setMessage(entry.getMessage());
        doc.setResult(entry.getResult());
        doc.setSource(extractSource(entry.getMessage()));
        doc.setCreatedAt(entry.getCreatedAt() != null
                ? LocalDateTime.parse(entry.getCreatedAt().toString()) : null);
        doc.setCompletedAt(entry.getCompletedAt() != null
                ? LocalDateTime.parse(entry.getCompletedAt().toString()) : null);
        logSearchRepository.save(doc);
    }

    private String extractSource(String message) {
        if (message == null) return "LOGSENSE_AI";
        if (message.contains("PAYROLL_SERVICE")) return "PAYROLL_SERVICE";
        return "LOGSENSE_AI";
    }
}