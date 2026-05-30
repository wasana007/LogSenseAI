package com.logai.service;

import com.logai.model.LogEntry;
import com.logai.model.LogStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LogNotificationServiceTest {

    private static final String TOPIC = "/topic/logs";
    private static final String CORRELATION_ID = "corr-123";
    private static final String MESSAGE = "Salary calculation failed";
    private static final String RESULT = "DB issue detected";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 1, 1, 10, 0, 5);

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private LogNotificationService logNotificationService;

    private LogEntry logEntry(LogStatus status, String result,
                              LocalDateTime completedAt) {
        LogEntry entry = new LogEntry();
        entry.setCorrelationId(CORRELATION_ID);
        entry.setMessage(MESSAGE);
        entry.setStatus(status);
        entry.setResult(result);
        entry.setCreatedAt(CREATED_AT);
        entry.setCompletedAt(completedAt);
        return entry;
    }

    private LogNotificationService.LogEntryDto captureDto() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(TOPIC), captor.capture());
        return (LogNotificationService.LogEntryDto) captor.getValue();
    }

    @Nested
    @DisplayName("notify() — WebSocket topic")
    class NotifyTopic {

        @Test
        @DisplayName("sends to the correct WebSocket topic")
        void sendsToCorrectTopic() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq(TOPIC), captor.capture());
        }
    }

    @Nested
    @DisplayName("notify() — DTO mapping")
    class DtoMapping {

        @Test
        @DisplayName("maps correlationId correctly")
        void mapsCorrelationId() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.correlationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("maps message correctly")
        void mapsMessage() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.message()).isEqualTo(MESSAGE);
        }

        @Test
        @DisplayName("maps result correctly")
        void mapsResult() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.result()).isEqualTo(RESULT);
        }

        @Test
        @DisplayName("maps status name when status is present")
        void mapsStatusName() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.status()).isEqualTo(LogStatus.COMPLETED.name());
        }

        @Test
        @DisplayName("maps status as null when status is absent")
        void mapsNullStatus() {
            logNotificationService.notify(logEntry(null, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.status()).isNull();
        }

        @Test
        @DisplayName("maps createdAt correctly")
        void mapsCreatedAt() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.createdAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("maps completedAt correctly")
        void mapsCompletedAt() {
            logNotificationService.notify(logEntry(LogStatus.COMPLETED, RESULT, COMPLETED_AT));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.completedAt()).isEqualTo(COMPLETED_AT);
        }

        @Test
        @DisplayName("maps null result correctly")
        void mapsNullResult() {
            logNotificationService.notify(logEntry(LogStatus.FAILED, null, null));

            LogNotificationService.LogEntryDto dto = captureDto();
            assertThat(dto.result()).isNull();
            assertThat(dto.completedAt()).isNull();
        }
    }
}