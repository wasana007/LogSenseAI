package com.logai.service;

import com.logai.agent.AgentService;
import com.logai.model.LogEntry;
import com.logai.model.LogStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogConsumerServiceTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final String LOG_MESSAGE = "ERROR: something went wrong";
    private static final String AGENT_RESULT = "DB issue detected";
    private static final String TOPIC = "log-topic";
    private static final String LLM_ERROR = "LLM unavailable";

    @Mock
    private AgentService agentService;

    @Mock
    private LogStorageService storageService;

    @Mock
    private LogNotificationService notificationService;

    @InjectMocks
    private LogConsumerService logConsumerService;

    private ConsumerRecord<String, String> consumerRecord(String key) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, key, LOG_MESSAGE);
    }

    private LogEntry logEntry(LogStatus status) {
        LogEntry entry = new LogEntry();
        entry.setCorrelationId(CORRELATION_ID);
        entry.setStatus(status);
        return entry;
    }

    @Nested
    @DisplayName("consume() — happy path")
    class HappyPath {

        @Test
        @DisplayName("processes log message and saves result")
        void processesAndSavesResult() {
            LogEntry entry = logEntry(LogStatus.COMPLETED);
            when(agentService.handle(LOG_MESSAGE)).thenReturn(AGENT_RESULT);
            when(storageService.saveResult(CORRELATION_ID, AGENT_RESULT)).thenReturn(entry);

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(agentService).handle(LOG_MESSAGE);
            verify(storageService).saveResult(CORRELATION_ID, AGENT_RESULT);
        }

        @Test
        @DisplayName("notifies after successful processing")
        void notifiesAfterSuccess() {
            LogEntry entry = logEntry(LogStatus.COMPLETED);
            when(agentService.handle(LOG_MESSAGE)).thenReturn(AGENT_RESULT);
            when(storageService.saveResult(CORRELATION_ID, AGENT_RESULT)).thenReturn(entry);

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(notificationService).notify(entry);
        }

        @Test
        @DisplayName("calls agent, storage, and notification in order")
        void callsInOrder() {
            LogEntry entry = logEntry(LogStatus.COMPLETED);
            when(agentService.handle(LOG_MESSAGE)).thenReturn(AGENT_RESULT);
            when(storageService.saveResult(CORRELATION_ID, AGENT_RESULT)).thenReturn(entry);

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            InOrder inOrder = inOrder(agentService, storageService, notificationService);
            inOrder.verify(agentService).handle(LOG_MESSAGE);
            inOrder.verify(storageService).saveResult(CORRELATION_ID, AGENT_RESULT);
            inOrder.verify(notificationService).notify(entry);
        }
    }

    @Nested
    @DisplayName("consume() — null correlationId")
    class NullCorrelationId {

        @Test
        @DisplayName("ignores message when correlationId is null")
        void ignoresMessageWithNullCorrelationId() {
            logConsumerService.consume(consumerRecord(null));

            verifyNoInteractions(agentService, storageService, notificationService);
        }
    }

    @Nested
    @DisplayName("consume() — failure path")
    class FailurePath {

        @Test
        @DisplayName("saves failed entry when agentService throws")
        void savesFailedWhenAgentThrows() {
            LogEntry failedEntry = logEntry(LogStatus.FAILED);
            when(agentService.handle(LOG_MESSAGE)).thenThrow(new RuntimeException(LLM_ERROR));
            when(storageService.saveFailed(eq(CORRELATION_ID), anyString())).thenReturn(failedEntry);

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(storageService).saveFailed(eq(CORRELATION_ID), contains(LLM_ERROR));
        }

        @Test
        @DisplayName("notifies after failed processing")
        void notifiesAfterFailure() {
            LogEntry failedEntry = logEntry(LogStatus.FAILED);
            when(agentService.handle(LOG_MESSAGE)).thenThrow(new RuntimeException(LLM_ERROR));
            when(storageService.saveFailed(eq(CORRELATION_ID), anyString())).thenReturn(failedEntry);

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(notificationService).notify(failedEntry);
        }

        @Test
        @DisplayName("does not call saveResult when agentService throws")
        void doesNotSaveResultOnFailure() {
            when(agentService.handle(LOG_MESSAGE)).thenThrow(new RuntimeException(LLM_ERROR));
            when(storageService.saveFailed(eq(CORRELATION_ID), anyString()))
                    .thenReturn(logEntry(LogStatus.FAILED));

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(storageService, never()).saveResult(any(), any());
        }

        @Test
        @DisplayName("saves error message prefixed with FEIL:")
        void savesErrorMessageWithPrefix() {
            when(agentService.handle(LOG_MESSAGE)).thenThrow(new RuntimeException("timeout"));
            when(storageService.saveFailed(eq(CORRELATION_ID), anyString()))
                    .thenReturn(logEntry(LogStatus.FAILED));

            logConsumerService.consume(consumerRecord(CORRELATION_ID));

            verify(storageService).saveFailed(CORRELATION_ID, "FEIL: timeout");
        }
    }
}