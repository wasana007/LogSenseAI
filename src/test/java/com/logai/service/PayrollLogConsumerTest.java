package com.logai.service;

import com.contracts.logai.v1.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.agent.AgentService;
import com.logai.model.LogStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollLogConsumerTest {

    private static final String TOPIC = "/topic/payroll-logs";
    private static final String KAFKA_TOPIC = "payroll-log-events";
    private static final String CORRELATION_ID = "corr-123";
    private static final String SOURCE = "PAYROLL_SERVICE";
    private static final String EMPLOYEE_ID = "emp-456";
    private static final String MESSAGE = "Salary calculation failed";
    private static final String AGENT_RESULT = "DB issue detected";
    private static final String LLM_ERROR = "LLM unavailable";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String LEVEL_INFO = "INFO";

    @Mock
    private AgentService agentService;

    @Mock
    private LogStorageService storageService;

    @Mock
    private SimpMessagingTemplate ws;

    @Mock
    private ObjectMapper mapper;

    @InjectMocks
    private PayrollLogConsumer payrollLogConsumer;

    private ConsumerRecord<String, String> consumerRecord(String value) {
        return new ConsumerRecord<>(KAFKA_TOPIC, 0, 0L, CORRELATION_ID, value);
    }

    private LogEvent logEvent(String level) {
        return new LogEvent(CORRELATION_ID, EMPLOYEE_ID, level, MESSAGE);
    }

    @Nested
    @DisplayName("consume() — parse failure")
    class ParseFailure {

        @Test
        @DisplayName("ignores message when JSON parsing fails")
        void ignoresOnParseFailure() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenThrow(new RuntimeException("Invalid JSON"));

            payrollLogConsumer.consume(consumerRecord("invalid json"));

            verifyNoInteractions(agentService, storageService, ws);
        }
    }

    @Nested
    @DisplayName("consume() — INFO level")
    class InfoLevel {

        @Test
        @DisplayName("ignores INFO level events")
        void ignoresInfoLevel() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_INFO));

            payrollLogConsumer.consume(consumerRecord("{}"));

            verifyNoInteractions(agentService, storageService, ws);
        }

        @Test
        @DisplayName("ignores INFO level regardless of case")
        void ignoresInfoLevelCaseInsensitive() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent("info"));

            payrollLogConsumer.consume(consumerRecord(CORRELATION_ID));

            verifyNoInteractions(agentService, storageService, ws);
        }
    }

    @Nested
    @DisplayName("consume() — duplicate detection")
    class DuplicateDetection {

        @Test
        @DisplayName("ignores duplicate correlationId")
        void ignoresDuplicate() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(false);

            payrollLogConsumer.consume(consumerRecord("{}"));

            verifyNoInteractions(agentService, ws);
        }
    }

    @Nested
    @DisplayName("consume() — happy path")
    class HappyPath {

        @Test
        @DisplayName("formats message with PAYROLL prefix before passing to agent")
        void formatsMessageWithPayrollPrefix() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenReturn(AGENT_RESULT);

            payrollLogConsumer.consume(consumerRecord("{}"));

            ArgumentCaptor<String> formattedCaptor = ArgumentCaptor.forClass(String.class);
            verify(storageService).savePendingIfAbsent(eq(CORRELATION_ID), formattedCaptor.capture());

            String formatted = formattedCaptor.getValue();
            assertThat(formatted)
                    .startsWith("[PAYROLL]")
                    .contains("source=" + SOURCE)
                    .contains("employeeId=" + EMPLOYEE_ID)
                    .contains("message=" + MESSAGE);
        }

        @Test
        @DisplayName("saves result after successful agent processing")
        void savesResultAfterProcessing() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenReturn(AGENT_RESULT);

            payrollLogConsumer.consume(consumerRecord("{}"));

            verify(storageService).saveResult(CORRELATION_ID, AGENT_RESULT);
        }

        @Test
        @DisplayName("sends COMPLETED status to WebSocket topic")
        void sendsCompletedToWebSocket() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenReturn(AGENT_RESULT);

            payrollLogConsumer.consume(consumerRecord("{}"));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(ws).convertAndSend(eq(TOPIC), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) captor.getValue();
            assertThat(payload)
                    .containsEntry("correlationId", CORRELATION_ID)
                    .containsEntry("status", LogStatus.COMPLETED.name())
                    .containsEntry("result", AGENT_RESULT)
                    .containsEntry("source", SOURCE)
                    .containsEntry("message", MESSAGE);
        }
    }

    @Nested
    @DisplayName("consume() — failure path")
    class FailurePath {

        @Test
        @DisplayName("saves failed entry when agent throws")
        void savesFailedWhenAgentThrows() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenThrow(new RuntimeException(LLM_ERROR));

            payrollLogConsumer.consume(consumerRecord("{}"));

            verify(storageService).saveFailed(CORRELATION_ID, LLM_ERROR);
        }

        @Test
        @DisplayName("sends FAILED status to WebSocket topic when agent throws")
        void sendsFailedToWebSocket() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenThrow(new RuntimeException(LLM_ERROR));

            payrollLogConsumer.consume(consumerRecord("{}"));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(ws).convertAndSend(eq(TOPIC), captor.capture());

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) captor.getValue();
            assertThat(payload)
                    .containsEntry("correlationId", CORRELATION_ID)
                    .containsEntry("status", LogStatus.FAILED.name())
                    .containsEntry("source", SOURCE)
                    .containsEntry("message", MESSAGE);
        }

        @Test
        @DisplayName("does not call saveResult when agent throws")
        void doesNotSaveResultOnFailure() throws Exception {
            when(mapper.readValue(anyString(), eq(LogEvent.class)))
                    .thenReturn(logEvent(LEVEL_ERROR));
            when(storageService.savePendingIfAbsent(eq(CORRELATION_ID), anyString()))
                    .thenReturn(true);
            when(agentService.handle(anyString())).thenThrow(new RuntimeException(LLM_ERROR));

            payrollLogConsumer.consume(consumerRecord("{}"));

            verify(storageService, never()).saveResult(any(), any());
        }
    }
}