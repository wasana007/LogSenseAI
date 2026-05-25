package com.logai.service;

import com.contracts.logai.v1.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.agent.AgentService;
import com.logai.model.LogStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PayrollLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(PayrollLogConsumer.class);
    private static final String TOPIC = "/topic/payroll-logs";

    private final AgentService agentService;
    private final LogStorageService storageService;
    private final SimpMessagingTemplate ws;
    private final ObjectMapper mapper;

    public PayrollLogConsumer(AgentService agentService,
                              LogStorageService storageService,
                              SimpMessagingTemplate ws,
                              ObjectMapper mapper) {
        this.agentService = agentService;
        this.storageService = storageService;
        this.ws = ws;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "payroll-log-events", groupId = "logai-payroll-group")
    public void consume(ConsumerRecord<String, String> consumerRecord) {

        String id = consumerRecord.key();
        String json = consumerRecord.value();

        LogEvent event;
        try {
            event = mapper.readValue(json, LogEvent.class);
        } catch (Exception e) {
            log.error("parse error id={}", id, e);
            return;
        }

        String level = event.getLevel();
        String message = event.getMessage();
        String source = event.getSource();
        String employeeId = event.getEmployeeId();

        if ("INFO".equalsIgnoreCase(level)) return;

        String formatted = "[PAYROLL] source=%s employeeId=%s timestamp=%s message=%s".formatted(
                source, employeeId, event.getTimestamp(), message
        );

        boolean saved = storageService.savePendingIfAbsent(id, formatted);
        if (!saved) {
            log.warn("Duplikat ignorert | correlationId={}", id);
            return;
        }

        try {
            String result = agentService.handle(formatted);
            storageService.saveResult(id, result);

            ws.convertAndSend(TOPIC, Map.of(
                    "correlationId", id,
                    "source", source,
                    "message", message,
                    "result", result,
                    "status", LogStatus.COMPLETED.name()
            ));

            log.info("done id={}", id);

        } catch (Exception e) {
            log.error("failed id={}", id, e);
            storageService.saveFailed(id, e.getMessage());

            ws.convertAndSend(TOPIC, Map.of(
                    "correlationId", id,
                    "source", source,
                    "message", message,
                    "status", LogStatus.FAILED.name()
            ));
        }
    }
}