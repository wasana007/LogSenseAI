package com.logai.service;

import com.logai.agent.AgentService;
import com.logai.model.LogEntry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class LogConsumerService {

    private static final Logger log = LoggerFactory.getLogger(LogConsumerService.class);

    private final AgentService agentService;
    private final LogStorageService storageService;
    private final LogNotificationService notificationService;

    public LogConsumerService(AgentService agentService,
                              LogStorageService storageService,
                              LogNotificationService notificationService) {
        this.agentService = agentService;
        this.storageService = storageService;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${kafka.topic.log}",
            groupId = "${kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> consumerRecord) {

        String correlationId = consumerRecord.key();
        String logMessage = consumerRecord.value();

        if (correlationId == null) {
            log.warn("Mottatt melding uten correlationId — ignorerer");
            return;
        }

        log.info("Mottatt | correlationId={}", correlationId);

        try {
            String result = agentService.handle(logMessage);
            LogEntry entry = storageService.saveResult(correlationId, result);
            notificationService.notify(entry);
            log.info("Fullført | correlationId={}", correlationId);

        } catch (Exception e) {
            LogEntry entry = storageService.saveFailed(correlationId, "FEIL: " + e.getMessage());
            notificationService.notify(entry);
            log.error("Mislyktes | correlationId={}", correlationId, e);
        }
    }
}